# coding=utf-8
"""
目标检测引擎
提供目标检测功能
"""

from typing import Union, List, Tuple
import numpy as np
from PIL import Image

from .base import BaseEngine
from ..utils.image_io import load_image_from_input, image_to_numpy
from ..utils.exceptions import ModelLoadError, ImageProcessError, safe_import_opencv
from ..utils.validators import validate_image_input

# 安全导入OpenCV
cv2 = safe_import_opencv()


class DetectionEngine(BaseEngine):
    """目标检测引擎"""

    def __init__(self, use_gpu: bool = False, device_id: int = 0):
        """
        初始化检测引擎

        Args:
            use_gpu: 是否使用GPU
            device_id: GPU设备ID
        """
        super().__init__(use_gpu, device_id)
        self.initialize()

    def initialize(self, **kwargs) -> None:
        """
        初始化检测引擎

        Raises:
            ModelLoadError: 当初始化失败时
        """
        try:
            # 加载检测模型
            self.session = self.model_loader.load_detection_model()
            self.is_initialized = True

        except Exception as e:
            raise ModelLoadError(f"检测引擎初始化失败: {str(e)}") from e

    def predict(self, image: Union[bytes, str, Image.Image]) -> List[List[int]]:
        """
        执行目标检测

        Args:
            image: 输入图像

        Returns:
            检测到的边界框列表，每个边界框格式为[x1, y1, x2, y2]

        Raises:
            ImageProcessError: 当图像处理失败时
            ModelLoadError: 当模型未初始化时
        """
        if not self.is_ready():
            raise ModelLoadError("检测引擎未初始化")

        # 验证输入
        validate_image_input(image)

        try:
            # 直接使用原始的get_bbox方法
            if isinstance(image, bytes):
                return self.get_bbox(image)
            elif isinstance(image, Image.Image):
                import io
                img_bytes = io.BytesIO()
                image.save(img_bytes, format='PNG')
                return self.get_bbox(img_bytes.getvalue())
            else:
                # 其他类型先转换为PIL Image再处理
                pil_image = load_image_from_input(image)
                import io
                img_bytes = io.BytesIO()
                pil_image.save(img_bytes, format='PNG')
                return self.get_bbox(img_bytes.getvalue())

        except Exception as e:
            raise ImageProcessError(f"目标检测失败: {str(e)}") from e

    def preproc(self, img, input_size, swap=(2, 0, 1)):
        """预处理函数（来自原始代码）"""
        if len(img.shape) == 3:
            padded_img = np.ones((input_size[0], input_size[1], 3), dtype=np.uint8) * 114
        else:
            padded_img = np.ones(input_size, dtype=np.uint8) * 114
        r = min(input_size[0] / img.shape[0], input_size[1] / img.shape[1])
        resized_img = cv2.resize(
            img,
            (int(img.shape[1] * r), int(img.shape[0] * r)),
            interpolation=cv2.INTER_LINEAR,
        ).astype(np.uint8)
        padded_img[: int(img.shape[0] * r), : int(img.shape[1] * r)] = resized_img
        padded_img = padded_img.transpose(swap)
        padded_img = np.ascontiguousarray(padded_img, dtype=np.float32)
        return padded_img, r

    def demo_postprocess(self, outputs, img_size, p6=False):
        """后处理函数（来自原始代码）"""
        grids = []
        expanded_strides = []
        if not p6:
            strides = [8, 16, 32]
        else:
            strides = [8, 16, 32, 64]
        hsizes = [img_size[0] // stride for stride in strides]
        wsizes = [img_size[1] // stride for stride in strides]
        for hsize, wsize, stride in zip(hsizes, wsizes, strides):
            xv, yv = np.meshgrid(np.arange(wsize), np.arange(hsize))
            grid = np.stack((xv, yv), 2).reshape(1, -1, 2)
            grids.append(grid)
            shape = grid.shape[:2]
            expanded_strides.append(np.full((*shape, 1), stride))
        grids = np.concatenate(grids, 1)
        expanded_strides = np.concatenate(expanded_strides, 1)
        outputs[..., :2] = (outputs[..., :2] + grids) * expanded_strides
        outputs[..., 2:4] = np.exp(outputs[..., 2:4]) * expanded_strides
        return outputs

    def nms(self, boxes, scores, nms_thr):
        """Single class NMS implemented in Numpy."""
        x1 = boxes[:, 0]
        y1 = boxes[:, 1]
        x2 = boxes[:, 2]
        y2 = boxes[:, 3]
        areas = (x2 - x1 + 1) * (y2 - y1 + 1)
        order = scores.argsort()[::-1]
        keep = []
        while order.size > 0:
            i = order[0]
            keep.append(i)
            xx1 = np.maximum(x1[i], x1[order[1:]])
            yy1 = np.maximum(y1[i], y1[order[1:]])
            xx2 = np.minimum(x2[i], x2[order[1:]])
            yy2 = np.minimum(y2[i], y2[order[1:]])
            w = np.maximum(0.0, xx2 - xx1 + 1)
            h = np.maximum(0.0, yy2 - yy1 + 1)
            inter = w * h
            ovr = inter / (areas[i] + areas[order[1:]] - inter)
            inds = np.where(ovr <= nms_thr)[0]
            order = order[inds + 1]
        return keep

    def multiclass_nms_class_agnostic(self, boxes, scores, nms_thr, score_thr):
        """Multiclass NMS implemented in Numpy. Class-agnostic version."""
        cls_inds = scores.argmax(1)
        cls_scores = scores[np.arange(len(cls_inds)), cls_inds]
        valid_score_mask = cls_scores > score_thr
        if valid_score_mask.sum() == 0:
            return None
        valid_scores = cls_scores[valid_score_mask]
        valid_boxes = boxes[valid_score_mask]
        valid_cls_inds = cls_inds[valid_score_mask]
        keep = self.nms(valid_boxes, valid_scores, nms_thr)
        if keep:
            dets = np.concatenate(
                [valid_boxes[keep], valid_scores[keep, None], valid_cls_inds[keep, None]], 1
            )
        return dets

    def multiclass_nms(self, boxes, scores, nms_thr, score_thr):
        """Multiclass NMS implemented in Numpy"""
        return self.multiclass_nms_class_agnostic(boxes, scores, nms_thr, score_thr)

    def get_bbox(self, image_bytes):
        """原始的目标检测方法"""
        img = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
        im, ratio = self.preproc(img, (416, 416))
        ort_inputs = {self.session.get_inputs()[0].name: im[None, :, :, :]}
        output = self.session.run(None, ort_inputs)
        predictions = self.demo_postprocess(output[0], (416, 416))[0]
        boxes = predictions[:, :4]
        scores = predictions[:, 4:5] * predictions[:, 5:]
        boxes_xyxy = np.ones_like(boxes)
        boxes_xyxy[:, 0] = boxes[:, 0] - boxes[:, 2] / 2.
        boxes_xyxy[:, 1] = boxes[:, 1] - boxes[:, 3] / 2.
        boxes_xyxy[:, 2] = boxes[:, 0] + boxes[:, 2] / 2.
        boxes_xyxy[:, 3] = boxes[:, 1] + boxes[:, 3] / 2.
        boxes_xyxy /= ratio
        pred = self.multiclass_nms(boxes_xyxy, scores, nms_thr=0.45, score_thr=0.1)
        try:
            final_boxes = pred[:, :4].tolist()
            result = []
            for b in final_boxes:
                if b[0] < 0:
                    x_min = 0
                else:
                    x_min = int(b[0])
                if b[1] < 0:
                    y_min = 0
                else:
                    y_min = int(b[1])
                if b[2] > img.shape[1]:
                    x_max = int(img.shape[1])
                else:
                    x_max = int(b[2])
                if b[3] > img.shape[0]:
                    y_max = int(img.shape[0])
                else:
                    y_max = int(b[3])
                result.append([x_min, y_min, x_max, y_max])
        except Exception as e:
            return []
        return result

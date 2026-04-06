# coding=utf-8
"""
API数据模型定义
"""

from typing import List, Optional, Union, Dict, Any
from pydantic import BaseModel, Field


class InitializeRequest(BaseModel):
    """初始化请求模型"""
    ocr: bool = Field(True, description="是否启用OCR功能")
    det: bool = Field(False, description="是否启用目标检测功能")
    old: bool = Field(False, description="是否使用旧版OCR模型")
    beta: bool = Field(False, description="是否使用beta版OCR模型")
    use_gpu: bool = Field(False, description="是否使用GPU")
    device_id: int = Field(0, description="GPU设备ID")
    import_onnx_path: str = Field("", description="自定义ONNX模型路径")
    charsets_path: str = Field("", description="自定义字符集路径")


class SwitchModelRequest(BaseModel):
    """切换模型请求模型"""
    model_type: str = Field(..., description="模型类型: 'ocr', 'det', 'ocr_old', 'ocr_beta'")
    use_gpu: bool = Field(False, description="是否使用GPU")
    device_id: int = Field(0, description="GPU设备ID")


class ToggleFeatureRequest(BaseModel):
    """开启/关闭功能请求模型"""
    feature: str = Field(..., description="功能名称: 'ocr', 'detection', 'color_filter'")
    enabled: bool = Field(..., description="是否启用")


class OCRRequest(BaseModel):
    """OCR识别请求模型"""
    image: str = Field(..., description="图片数据（base64编码）")
    png_fix: bool = Field(False, description="是否修复PNG透明背景问题")
    probability: bool = Field(False, description="是否返回概率信息")
    color_filter_colors: Optional[List[str]] = Field(None, description="颜色过滤预设颜色列表")
    color_filter_custom_ranges: Optional[List[List[List[int]]]] = Field(None, description="自定义HSV颜色范围")
    charset_range: Optional[Union[int, str]] = Field(None, description="字符集范围限制")


class DetectionRequest(BaseModel):
    """目标检测请求模型"""
    image: str = Field(..., description="图片数据（base64编码）")


class SlideMatchRequest(BaseModel):
    """滑块匹配请求模型"""
    target_image: str = Field(..., description="滑块图片（base64编码）")
    background_image: str = Field(..., description="背景图片（base64编码）")
    simple_target: bool = Field(False, description="是否为简单滑块")


class SlideComparisonRequest(BaseModel):
    """滑块比较请求模型"""
    target_image: str = Field(..., description="带坑位的图片（base64编码）")
    background_image: str = Field(..., description="完整背景图片（base64编码）")


class APIResponse(BaseModel):
    """API响应基础模型"""
    success: bool = Field(..., description="请求是否成功")
    message: str = Field("", description="响应消息")
    data: Optional[Any] = Field(None, description="响应数据")


class StatusResponse(BaseModel):
    """状态响应模型"""
    service_status: str = Field(..., description="服务状态")
    loaded_models: List[str] = Field(..., description="已加载的模型列表")
    enabled_features: List[str] = Field(..., description="已启用的功能列表")
    version: str = Field(..., description="版本信息")
    uptime: float = Field(..., description="运行时间（秒）")


class OCRResponse(BaseModel):
    """OCR识别响应模型"""
    text: Optional[str] = Field(None, description="识别的文本")
    probability: Optional[Dict[str, Any]] = Field(None, description="概率信息")


class DetectionResponse(BaseModel):
    """目标检测响应模型"""
    bboxes: List[List[int]] = Field(..., description="检测到的边界框列表")


class SlideResponse(BaseModel):
    """滑块响应模型"""
    target: List[int] = Field(..., description="目标位置坐标")
    target_x: Optional[int] = Field(None, description="滑块X偏移")
    target_y: Optional[int] = Field(None, description="滑块Y偏移")


# MCP协议相关模型
class MCPRequest(BaseModel):
    """MCP请求模型"""
    method: str = Field(..., description="方法名")
    params: Dict[str, Any] = Field({}, description="参数")
    id: Optional[Union[str, int]] = Field(None, description="请求ID")


class MCPResponse(BaseModel):
    """MCP响应模型"""
    result: Optional[Any] = Field(None, description="结果")
    error: Optional[Dict[str, Any]] = Field(None, description="错误信息")
    id: Optional[Union[str, int]] = Field(None, description="请求ID")


class MCPCapabilities(BaseModel):
    """MCP能力声明模型"""
    tools: List[Dict[str, Any]] = Field(..., description="可用工具列表")
    resources: List[Dict[str, Any]] = Field([], description="可用资源列表")
    prompts: List[Dict[str, Any]] = Field([], description="可用提示列表")

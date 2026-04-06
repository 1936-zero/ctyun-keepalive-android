# coding=utf-8
"""
API路由定义
"""

import base64
import time
import traceback
from typing import Dict, Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, HTMLResponse

from .models import *


def create_routes(app: FastAPI, service):
    """创建API路由"""
    
    @app.get("/", response_class=HTMLResponse)
    async def root():
        """根路径，返回API文档链接"""
        return """
        <html>
            <head>
                <title>DDDDOCR API</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    .container { max-width: 800px; margin: 0 auto; }
                    .header { text-align: center; margin-bottom: 40px; }
                    .links { display: flex; justify-content: center; gap: 20px; }
                    .link { padding: 10px 20px; background: #007bff; color: white; text-decoration: none; border-radius: 5px; }
                    .link:hover { background: #0056b3; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>DDDDOCR API 服务</h1>
                        <p>带带弟弟OCR通用验证码识别API服务</p>
                    </div>
                    <div class="links">
                        <a href="/docs" class="link">Swagger UI 文档</a>
                        <a href="/redoc" class="link">ReDoc 文档</a>
                        <a href="/status" class="link">服务状态</a>
                    </div>
                </div>
            </body>
        </html>
        """
    
    @app.post("/initialize", response_model=APIResponse)
    async def initialize(request: InitializeRequest):
        """初始化并选择加载的模型类型"""
        try:
            result = service.initialize(request)
            return APIResponse(success=True, message=result["message"], data=result)
        except Exception as e:
            return APIResponse(success=False, message=str(e))
    
    @app.post("/switch-model", response_model=APIResponse)
    async def switch_model(request: SwitchModelRequest):
        """运行时切换模型配置"""
        try:
            result = service.switch_model(request)
            return APIResponse(success=True, message=result["message"], data=result)
        except Exception as e:
            return APIResponse(success=False, message=str(e))
    
    @app.post("/toggle-feature", response_model=APIResponse)
    async def toggle_feature(request: ToggleFeatureRequest):
        """开启/关闭特定功能"""
        try:
            result = service.toggle_feature(request)
            return APIResponse(success=True, message=result["message"], data=result)
        except Exception as e:
            return APIResponse(success=False, message=str(e))
    
    @app.post("/ocr", response_model=APIResponse)
    async def ocr_recognition(request: OCRRequest):
        """执行OCR识别"""
        try:
            if not service.ocr_instance:
                raise HTTPException(status_code=400, detail="OCR功能未初始化，请先调用 /initialize 接口")
            
            if "ocr" not in service.enabled_features:
                raise HTTPException(status_code=400, detail="OCR功能已禁用")
            
            # 解码base64图片
            try:
                image_data = base64.b64decode(request.image)
            except Exception:
                raise HTTPException(status_code=400, detail="图片base64解码失败")
            
            # 设置字符集范围
            if request.charset_range is not None:
                service.ocr_instance.set_ranges(request.charset_range)
            
            # 执行OCR识别
            result = service.ocr_instance.classification(
                image_data,
                png_fix=request.png_fix,
                probability=request.probability,
                color_filter_colors=request.color_filter_colors,
                color_filter_custom_ranges=request.color_filter_custom_ranges
            )
            
            if request.probability:
                response_data = OCRResponse(text=None, probability=result)
            else:
                response_data = OCRResponse(text=result, probability=None)
            
            return APIResponse(success=True, message="OCR识别成功", data=response_data.dict())
            
        except HTTPException:
            raise
        except Exception as e:
            return APIResponse(success=False, message=f"OCR识别失败: {str(e)}")
    
    @app.post("/detect", response_model=APIResponse)
    async def object_detection(request: DetectionRequest):
        """执行目标检测"""
        try:
            if not service.det_instance:
                raise HTTPException(status_code=400, detail="目标检测功能未初始化，请先调用 /initialize 接口")
            
            if "detection" not in service.enabled_features:
                raise HTTPException(status_code=400, detail="目标检测功能已禁用")
            
            # 解码base64图片
            try:
                image_data = base64.b64decode(request.image)
            except Exception:
                raise HTTPException(status_code=400, detail="图片base64解码失败")
            
            # 执行目标检测
            bboxes = service.det_instance.detection(image_data)
            
            response_data = DetectionResponse(bboxes=bboxes)
            return APIResponse(success=True, message="目标检测成功", data=response_data.dict())
            
        except HTTPException:
            raise
        except Exception as e:
            return APIResponse(success=False, message=f"目标检测失败: {str(e)}")
    
    @app.post("/slide-match", response_model=APIResponse)
    async def slide_match(request: SlideMatchRequest):
        """滑块匹配"""
        try:
            if not service.slide_instance:
                raise HTTPException(status_code=500, detail="滑块功能未初始化")
            
            # 解码base64图片
            try:
                target_data = base64.b64decode(request.target_image)
                background_data = base64.b64decode(request.background_image)
            except Exception:
                raise HTTPException(status_code=400, detail="图片base64解码失败")
            
            # 执行滑块匹配
            result = service.slide_instance.slide_match(
                target_data, background_data, simple_target=request.simple_target
            )
            
            response_data = SlideResponse(**result)
            return APIResponse(success=True, message="滑块匹配成功", data=response_data.dict())
            
        except HTTPException:
            raise
        except Exception as e:
            return APIResponse(success=False, message=f"滑块匹配失败: {str(e)}")
    
    @app.post("/slide-comparison", response_model=APIResponse)
    async def slide_comparison(request: SlideComparisonRequest):
        """滑块比较"""
        try:
            if not service.slide_instance:
                raise HTTPException(status_code=500, detail="滑块功能未初始化")
            
            # 解码base64图片
            try:
                target_data = base64.b64decode(request.target_image)
                background_data = base64.b64decode(request.background_image)
            except Exception:
                raise HTTPException(status_code=400, detail="图片base64解码失败")
            
            # 执行滑块比较
            result = service.slide_instance.slide_comparison(target_data, background_data)
            
            response_data = SlideResponse(**result)
            return APIResponse(success=True, message="滑块比较成功", data=response_data.dict())
            
        except HTTPException:
            raise
        except Exception as e:
            return APIResponse(success=False, message=f"滑块比较失败: {str(e)}")
    
    @app.get("/status", response_model=StatusResponse)
    async def get_status():
        """获取当前服务状态和已加载的模型信息"""
        return service.get_status()
    
    @app.get("/health")
    async def health_check():
        """健康检查"""
        return {"status": "healthy", "timestamp": time.time()}
    
    @app.exception_handler(Exception)
    async def global_exception_handler(request: Request, exc: Exception):
        """全局异常处理"""
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "message": f"服务器内部错误: {str(exc)}",
                "detail": traceback.format_exc() if app.debug else None
            }
        )

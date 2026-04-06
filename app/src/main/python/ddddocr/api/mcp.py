# coding=utf-8
"""
MCP (Model Context Protocol) 协议支持
使AI Agent能够调用ddddocr服务
"""

import json
import base64
from typing import Dict, Any, List

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import JSONResponse

from .models import MCPRequest, MCPResponse, MCPCapabilities


class MCPHandler:
    """MCP协议处理器"""
    
    def __init__(self, service):
        self.service = service
        self.router = APIRouter()
        self._setup_routes()
    
    def _setup_routes(self):
        """设置MCP路由"""
        
        @self.router.get("/capabilities")
        async def get_capabilities():
            """获取MCP能力声明"""
            capabilities = MCPCapabilities(
                tools=[
                    {
                        "name": "ddddocr_initialize",
                        "description": "初始化DDDDOCR服务，选择加载的模型类型",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "ocr": {"type": "boolean", "description": "是否启用OCR功能"},
                                "det": {"type": "boolean", "description": "是否启用目标检测功能"},
                                "old": {"type": "boolean", "description": "是否使用旧版OCR模型"},
                                "beta": {"type": "boolean", "description": "是否使用beta版OCR模型"},
                                "use_gpu": {"type": "boolean", "description": "是否使用GPU"}
                            }
                        }
                    },
                    {
                        "name": "ddddocr_ocr",
                        "description": "执行OCR文字识别",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "image": {"type": "string", "description": "图片数据（base64编码）"},
                                "png_fix": {"type": "boolean", "description": "是否修复PNG透明背景问题"},
                                "probability": {"type": "boolean", "description": "是否返回概率信息"},
                                "color_filter_colors": {
                                    "type": "array", 
                                    "items": {"type": "string"},
                                    "description": "颜色过滤预设颜色列表，如 ['red', 'blue']"
                                },
                                "charset_range": {
                                    "oneOf": [
                                        {"type": "integer"},
                                        {"type": "string"}
                                    ],
                                    "description": "字符集范围限制"
                                }
                            },
                            "required": ["image"]
                        }
                    },
                    {
                        "name": "ddddocr_detection",
                        "description": "执行目标检测",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "image": {"type": "string", "description": "图片数据（base64编码）"}
                            },
                            "required": ["image"]
                        }
                    },
                    {
                        "name": "ddddocr_slide_match",
                        "description": "滑块匹配算法",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "target_image": {"type": "string", "description": "滑块图片（base64编码）"},
                                "background_image": {"type": "string", "description": "背景图片（base64编码）"},
                                "simple_target": {"type": "boolean", "description": "是否为简单滑块"}
                            },
                            "required": ["target_image", "background_image"]
                        }
                    },
                    {
                        "name": "ddddocr_slide_comparison",
                        "description": "滑块比较算法",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "target_image": {"type": "string", "description": "带坑位的图片（base64编码）"},
                                "background_image": {"type": "string", "description": "完整背景图片（base64编码）"}
                            },
                            "required": ["target_image", "background_image"]
                        }
                    },
                    {
                        "name": "ddddocr_status",
                        "description": "获取服务状态信息",
                        "inputSchema": {
                            "type": "object",
                            "properties": {}
                        }
                    }
                ]
            )
            return capabilities
        
        @self.router.post("/call")
        async def call_tool(request: MCPRequest):
            """调用MCP工具"""
            try:
                method = request.method
                params = request.params
                
                if method == "ddddocr_initialize":
                    from .models import InitializeRequest
                    init_request = InitializeRequest(**params)
                    result = self.service.initialize(init_request)
                    
                elif method == "ddddocr_ocr":
                    from .models import OCRRequest
                    ocr_request = OCRRequest(**params)
                    
                    if not self.service.ocr_instance:
                        raise HTTPException(status_code=400, detail="OCR功能未初始化")
                    
                    # 解码base64图片
                    image_data = base64.b64decode(ocr_request.image)
                    
                    # 设置字符集范围
                    if ocr_request.charset_range is not None:
                        self.service.ocr_instance.set_ranges(ocr_request.charset_range)
                    
                    # 执行OCR识别
                    result = self.service.ocr_instance.classification(
                        image_data,
                        png_fix=ocr_request.png_fix,
                        probability=ocr_request.probability,
                        color_filter_colors=ocr_request.color_filter_colors,
                        color_filter_custom_ranges=ocr_request.color_filter_custom_ranges
                    )
                    
                elif method == "ddddocr_detection":
                    from .models import DetectionRequest
                    det_request = DetectionRequest(**params)
                    
                    if not self.service.det_instance:
                        raise HTTPException(status_code=400, detail="目标检测功能未初始化")
                    
                    # 解码base64图片
                    image_data = base64.b64decode(det_request.image)
                    
                    # 执行目标检测
                    result = self.service.det_instance.detection(image_data)
                    
                elif method == "ddddocr_slide_match":
                    from .models import SlideMatchRequest
                    slide_request = SlideMatchRequest(**params)
                    
                    if not self.service.slide_instance:
                        raise HTTPException(status_code=500, detail="滑块功能未初始化")
                    
                    # 解码base64图片
                    target_data = base64.b64decode(slide_request.target_image)
                    background_data = base64.b64decode(slide_request.background_image)
                    
                    # 执行滑块匹配
                    result = self.service.slide_instance.slide_match(
                        target_data, background_data, simple_target=slide_request.simple_target
                    )
                    
                elif method == "ddddocr_slide_comparison":
                    from .models import SlideComparisonRequest
                    slide_request = SlideComparisonRequest(**params)
                    
                    if not self.service.slide_instance:
                        raise HTTPException(status_code=500, detail="滑块功能未初始化")
                    
                    # 解码base64图片
                    target_data = base64.b64decode(slide_request.target_image)
                    background_data = base64.b64decode(slide_request.background_image)
                    
                    # 执行滑块比较
                    result = self.service.slide_instance.slide_comparison(target_data, background_data)
                    
                elif method == "ddddocr_status":
                    result = self.service.get_status().dict()
                    
                else:
                    raise HTTPException(status_code=400, detail=f"不支持的方法: {method}")
                
                return MCPResponse(result=result, id=request.id)
                
            except Exception as e:
                return MCPResponse(
                    error={
                        "code": -1,
                        "message": str(e),
                        "data": None
                    },
                    id=request.id
                )
        
        @self.router.get("/")
        async def mcp_info():
            """MCP协议信息"""
            return {
                "protocol": "MCP",
                "version": "1.6.1",
                "description": "DDDDOCR MCP协议支持",
                "endpoints": {
                    "capabilities": "/mcp/capabilities",
                    "call": "/mcp/call"
                }
            }

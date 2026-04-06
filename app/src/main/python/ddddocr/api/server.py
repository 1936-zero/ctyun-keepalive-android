# coding=utf-8
"""
FastAPI服务器实现
"""

import time
import base64
import traceback
from typing import Optional, Dict, Any
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from .models import *
from .routes import create_routes
from .mcp import MCPHandler


class DDDDOCRService:
    """DDDDOCR服务管理类"""
    
    def __init__(self):
        self.ocr_instance = None
        self.det_instance = None
        self.slide_instance = None
        self.enabled_features = set()
        self.start_time = time.time()
        self.version = "1.6.1"
    
    def initialize(self, config: InitializeRequest) -> Dict[str, Any]:
        """初始化服务"""
        try:
            # 动态导入ddddocr以避免循环导入
            import ddddocr
            
            # 清理现有实例
            self.ocr_instance = None
            self.det_instance = None
            self.slide_instance = None
            self.enabled_features.clear()
            
            # 根据配置初始化实例
            if config.ocr:
                self.ocr_instance = ddddocr.DdddOcr(
                    ocr=True, 
                    det=False,
                    old=config.old,
                    beta=config.beta,
                    use_gpu=config.use_gpu,
                    device_id=config.device_id,
                    show_ad=False,
                    import_onnx_path=config.import_onnx_path,
                    charsets_path=config.charsets_path
                )
                self.enabled_features.add("ocr")
            
            if config.det:
                self.det_instance = ddddocr.DdddOcr(
                    ocr=False,
                    det=True,
                    use_gpu=config.use_gpu,
                    device_id=config.device_id,
                    show_ad=False
                )
                self.enabled_features.add("detection")
            
            # 滑块功能总是可用
            self.slide_instance = ddddocr.DdddOcr(ocr=False, det=False, show_ad=False)
            self.enabled_features.add("slide")
            
            return {
                "loaded_models": list(self.enabled_features),
                "message": "服务初始化成功"
            }
            
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"初始化失败: {str(e)}")
    
    def switch_model(self, config: SwitchModelRequest) -> Dict[str, Any]:
        """切换模型"""
        try:
            import ddddocr
            
            if config.model_type == "ocr":
                self.ocr_instance = ddddocr.DdddOcr(
                    ocr=True, det=False, old=False, beta=False,
                    use_gpu=config.use_gpu, device_id=config.device_id, show_ad=False
                )
                self.enabled_features.add("ocr")
            elif config.model_type == "ocr_old":
                self.ocr_instance = ddddocr.DdddOcr(
                    ocr=True, det=False, old=True, beta=False,
                    use_gpu=config.use_gpu, device_id=config.device_id, show_ad=False
                )
                self.enabled_features.add("ocr")
            elif config.model_type == "ocr_beta":
                self.ocr_instance = ddddocr.DdddOcr(
                    ocr=True, det=False, old=False, beta=True,
                    use_gpu=config.use_gpu, device_id=config.device_id, show_ad=False
                )
                self.enabled_features.add("ocr")
            elif config.model_type == "det":
                self.det_instance = ddddocr.DdddOcr(
                    ocr=False, det=True,
                    use_gpu=config.use_gpu, device_id=config.device_id, show_ad=False
                )
                self.enabled_features.add("detection")
            else:
                raise ValueError(f"不支持的模型类型: {config.model_type}")
            
            return {
                "model_type": config.model_type,
                "message": f"模型 {config.model_type} 切换成功"
            }
            
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"模型切换失败: {str(e)}")
    
    def toggle_feature(self, config: ToggleFeatureRequest) -> Dict[str, Any]:
        """开启/关闭功能"""
        if config.enabled:
            self.enabled_features.add(config.feature)
            message = f"功能 {config.feature} 已启用"
        else:
            self.enabled_features.discard(config.feature)
            message = f"功能 {config.feature} 已禁用"
        
        return {
            "feature": config.feature,
            "enabled": config.enabled,
            "message": message
        }
    
    def get_status(self) -> StatusResponse:
        """获取服务状态"""
        loaded_models = []
        if self.ocr_instance:
            loaded_models.append("ocr")
        if self.det_instance:
            loaded_models.append("detection")
        if self.slide_instance:
            loaded_models.append("slide")
        
        return StatusResponse(
            service_status="running",
            loaded_models=loaded_models,
            enabled_features=list(self.enabled_features),
            version=self.version,
            uptime=time.time() - self.start_time
        )


# 全局服务实例
service = DDDDOCRService()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时初始化
    print("DDDDOCR API服务启动中...")
    yield
    # 关闭时清理
    print("DDDDOCR API服务关闭中...")


def create_app() -> FastAPI:
    """创建FastAPI应用"""
    app = FastAPI(
        title="DDDDOCR API",
        description="带带弟弟OCR通用验证码识别API服务",
        version="1.6.1",
        docs_url="/docs",
        redoc_url="/redoc",
        lifespan=lifespan
    )
    
    # 添加CORS中间件
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    
    # 添加路由
    create_routes(app, service)
    
    # 添加MCP处理器
    mcp_handler = MCPHandler(service)
    app.include_router(mcp_handler.router, prefix="/mcp", tags=["MCP"])
    
    return app


def run_server(host: str = "0.0.0.0", port: int = 8000, **kwargs):
    """运行服务器"""
    app = create_app()
    print(f"DDDDOCR API服务启动在 http://{host}:{port}")
    print(f"API文档地址: http://{host}:{port}/docs")
    print(f"MCP协议地址: http://{host}:{port}/mcp")
    uvicorn.run(app, host=host, port=port, **kwargs)

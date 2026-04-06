# coding=utf-8
"""
基础引擎类
定义所有引擎的基础接口和通用功能
"""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional
import onnxruntime

from ..models.model_loader import ModelLoader
from ..utils.exceptions import ModelLoadError


class BaseEngine(ABC):
    """基础引擎抽象类"""
    
    def __init__(self, use_gpu: bool = False, device_id: int = 0):
        """
        初始化基础引擎
        
        Args:
            use_gpu: 是否使用GPU
            device_id: GPU设备ID
        """
        self.use_gpu = use_gpu
        self.device_id = device_id
        self.model_loader = ModelLoader(use_gpu, device_id)
        self.session: Optional[onnxruntime.InferenceSession] = None
        self.is_initialized = False
    
    @abstractmethod
    def initialize(self, **kwargs) -> None:
        """
        初始化引擎
        
        Args:
            **kwargs: 初始化参数
            
        Raises:
            ModelLoadError: 当初始化失败时
        """
        pass
    
    @abstractmethod
    def predict(self, *args, **kwargs) -> Any:
        """
        执行预测
        
        Args:
            *args: 位置参数
            **kwargs: 关键字参数
            
        Returns:
            预测结果
        """
        pass
    
    def get_model_info(self) -> Dict[str, Any]:
        """
        获取模型信息
        
        Returns:
            模型信息字典
        """
        if self.session:
            return self.model_loader.get_model_info(self.session)
        return {'error': '模型未加载'}
    
    def is_ready(self) -> bool:
        """
        检查引擎是否就绪
        
        Returns:
            是否就绪
        """
        return self.is_initialized and self.session is not None
    
    def switch_device(self, use_gpu: bool, device_id: int = 0) -> None:
        """
        切换计算设备
        
        Args:
            use_gpu: 是否使用GPU
            device_id: GPU设备ID
        """
        if self.use_gpu != use_gpu or self.device_id != device_id:
            self.use_gpu = use_gpu
            self.device_id = device_id
            self.model_loader.switch_provider(use_gpu, device_id)
            
            # 如果已经初始化，需要重新加载模型
            if self.is_initialized:
                self._reload_model()
    
    def _reload_model(self) -> None:
        """重新加载模型（子类可重写）"""
        pass
    
    def cleanup(self) -> None:
        """清理资源"""
        if self.session:
            del self.session
            self.session = None
        self.is_initialized = False
    
    def __del__(self):
        """析构函数"""
        self.cleanup()
    
    def __repr__(self) -> str:
        return f"{self.__class__.__name__}(use_gpu={self.use_gpu}, device_id={self.device_id}, ready={self.is_ready()})"

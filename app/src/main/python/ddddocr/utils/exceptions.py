# coding=utf-8
"""
异常处理模块
定义项目中使用的自定义异常类
"""

import sys
import platform


class DDDDOCRError(Exception):
    """DDDDOCR基础异常类"""
    pass


class ModelLoadError(DDDDOCRError):
    """模型加载异常"""
    pass


class ImageProcessError(DDDDOCRError):
    """图像处理异常"""
    pass


class TypeError(Exception):
    """类型错误异常（保持向后兼容）"""
    pass


def handle_opencv_import_error(error: ImportError) -> None:
    """
    处理OpenCV导入错误，提供详细的解决方案
    
    Args:
        error: ImportError异常对象
    """
    error_msg = f"""
OpenCV导入失败: {str(error)}

常见解决方案：

1. 重新安装opencv-python:
   pip uninstall opencv-python opencv-python-headless
   pip install opencv-python-headless

2. 系统特定解决方案：
"""
    
    system = platform.system().lower()
    if system == "linux":
        error_msg += """
   Linux系统：
   - Ubuntu/Debian: sudo apt-get install build-essential libglib2.0-0 libsm6 libxext6 libxrender-dev libgl1-mesa-glx
   - CentOS/RHEL: sudo yum install gcc gcc-c++ make glib2-devel libSM libXext libXrender mesa-libGL
   - 或尝试: sudo apt-get install python3-opencv
"""
    elif system == "windows":
        error_msg += """
   Windows系统：
   - 安装Visual C++运行库: https://www.ghxi.com/yxkhj.html
   - 确保使用64位Python版本
   - 尝试: pip install opencv-python --force-reinstall
"""
    elif system == "darwin":  # macOS
        error_msg += """
   macOS系统：
   - 使用Homebrew: brew install opencv
   - 或使用conda: conda install opencv
   - M1/M2芯片参考: https://github.com/sml2h3/ddddocr/issues/67
"""
    
    error_msg += """
3. 如果问题持续存在，请访问项目Issues页面寻求帮助：
   https://github.com/sml2h3/ddddocr/issues

注意：请不要自动安装依赖，按照上述指导手动安装。
"""
    
    print(error_msg)
    raise ImportError("OpenCV导入失败，请参考上述解决方案") from error


def safe_import_opencv():
    """
    安全导入OpenCV，如果失败则提供详细错误信息
    
    Returns:
        cv2模块对象
        
    Raises:
        ImportError: 当OpenCV导入失败时
    """
    try:
        import cv2
        return cv2
    except ImportError as e:
        class _MissingOpenCV:
            def __getattr__(self, name):
                raise ImportError("OpenCV 未集成到当前 Android 包中，仅支持 OCR 主路径") from e

        return _MissingOpenCV()

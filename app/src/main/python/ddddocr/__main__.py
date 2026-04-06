#!/usr/bin/env python
# -*- coding: utf-8 -*-
import argparse
import sys
import os


def main():
    """
    ddddocr 命令行入口点
    """
    parser = argparse.ArgumentParser(description='DdddOcr 通用验证码识别工具')
    
    # API 子命令
    subparsers = parser.add_subparsers(dest='command', help='子命令')
    
    # API 服务子命令
    api_parser = subparsers.add_parser('api', help='启动 API 服务')
    
    # API 服务配置
    api_parser.add_argument('--host', type=str, default='0.0.0.0',
                           help='API 服务主机地址，默认 0.0.0.0')
    api_parser.add_argument('--port', type=int, default=8000,
                           help='API 服务端口，默认 8000')
    api_parser.add_argument('--workers', type=int, default=1,
                           help='API 服务工作进程数，默认为 1')
    
    # OCR 引擎配置
    api_parser.add_argument('--ocr', type=lambda x: x.lower() == 'true', default=True,
                           help='是否启用 OCR 功能（true/false）')
    api_parser.add_argument('--det', type=lambda x: x.lower() == 'true', default=False,
                           help='是否启用目标检测功能（true/false）')
    api_parser.add_argument('--old', type=lambda x: x.lower() == 'true', default=False,
                           help='是否使用旧版 OCR 模型（true/false）')
    api_parser.add_argument('--beta', type=lambda x: x.lower() == 'true', default=False,
                           help='是否使用 Beta 版 OCR 模型（true/false）')
    api_parser.add_argument('--use-gpu', type=lambda x: x.lower() == 'true', default=False,
                           help='是否使用 GPU 加速（true/false）')
    api_parser.add_argument('--device-id', type=int, default=0,
                           help='GPU 设备 ID，默认 0')
    api_parser.add_argument('--show-ad', type=lambda x: x.lower() == 'true', default=True,
                           help='是否显示广告（true/false）')
    api_parser.add_argument('--import-onnx-path', type=str, default='',
                           help='自定义模型路径')
    api_parser.add_argument('--charsets-path', type=str, default='',
                           help='自定义字符集路径')
    
    # 解析命令行参数
    args = parser.parse_args()
    
    # 处理命令
    if args.command == 'api':
        # 设置环境变量
        os.environ['DDDDOCR_HOST'] = args.host
        os.environ['DDDDOCR_PORT'] = str(args.port)
        os.environ['DDDDOCR_WORKERS'] = str(args.workers)
        os.environ['DDDDOCR_OCR'] = str(args.ocr).lower()
        os.environ['DDDDOCR_DET'] = str(args.det).lower()
        os.environ['DDDDOCR_OLD'] = str(args.old).lower()
        os.environ['DDDDOCR_BETA'] = str(args.beta).lower()
        os.environ['DDDDOCR_USE_GPU'] = str(args.use_gpu).lower()
        os.environ['DDDDOCR_DEVICE_ID'] = str(args.device_id)
        os.environ['DDDDOCR_SHOW_AD'] = str(args.show_ad).lower()
        os.environ['DDDDOCR_IMPORT_ONNX_PATH'] = args.import_onnx_path
        os.environ['DDDDOCR_CHARSETS_PATH'] = args.charsets_path
        
        # 导入 API 模块并启动服务
        from ddddocr.api import main as api_main
        api_main()
    else:
        # 没有提供子命令，显示帮助
        parser.print_help()
        return 1
    
    return 0


if __name__ == '__main__':
    sys.exit(main()) 

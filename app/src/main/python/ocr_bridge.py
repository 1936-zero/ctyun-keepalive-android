import base64
import io

from PIL import Image
import ddddocr


class OfflineOcr:
    def __init__(self):
        self._ocr = ddddocr.DdddOcr(show_ad=False)

    def classify(self, image_base64: str) -> str:
        raw = base64.b64decode(image_base64)
        with Image.open(io.BytesIO(raw)) as image:
            buffer = io.BytesIO()
            image.save(buffer, format="PNG")
            result = self._ocr.classification(buffer.getvalue())
        return str(result).strip()


_INSTANCE = OfflineOcr()


def classify(image_base64: str) -> str:
    return _INSTANCE.classify(image_base64)

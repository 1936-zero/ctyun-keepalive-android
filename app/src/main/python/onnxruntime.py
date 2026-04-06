import numpy as np
from java import jarray, jclass, jfloat, jlong


Bridge = jclass("com.monkeycode.ctyunkeepalive.ocr.OrtPythonBridge")


def set_default_logger_severity(level):
    return None


def get_available_providers():
    return ["CPUExecutionProvider"]


class _Meta:
    def __init__(self, meta):
        self.name = str(meta.getName())
        self.shape = [int(x) for x in meta.getShape()]
        self.type = str(meta.getType())


class InferenceSession:
    def __init__(self, model_path, providers=None, sess_options=None):
        self._handle = Bridge.createSession(str(model_path))
        self._providers = list(providers or ["CPUExecutionProvider"])

    def get_inputs(self):
        return [_Meta(item) for item in Bridge.getInputs(self._handle)]

    def get_outputs(self):
        return [_Meta(item) for item in Bridge.getOutputs(self._handle)]

    def get_providers(self):
        return [str(item) for item in Bridge.getProviders(self._handle)]

    def run(self, output_names, input_feed):
        input_name, input_value = next(iter(input_feed.items()))
        array = np.asarray(input_value, dtype=np.float32)
        flat = jarray(jfloat)(array.reshape(-1).tolist())
        shape = jarray(jlong)([int(x) for x in array.shape])
        outputs = Bridge.run(self._handle, str(input_name), flat, shape)
        result = []
        for item in outputs:
            shape_list = [int(x) for x in item.getShape()]
            data = np.array(list(item.getData()), dtype=np.float32)
            result.append(data.reshape(shape_list))
        return result

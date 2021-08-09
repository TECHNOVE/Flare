package co.technove.flare.internal.profiling.dictionary;

import co.technove.flare.proto.ProfilerFileProto;
import one.jfr.ClassRef;
import one.jfr.Dictionary;
import one.jfr.JfrReader;
import one.jfr.MethodRef;

import java.nio.charset.StandardCharsets;

public abstract class TypeValue {
    private static final int FRAME_KERNEL = 5;
    private final JFRMethodType methodType;

    protected TypeValue(JFRMethodType methodType) {
        this.methodType = methodType;
    }

    public static TypeValue getMethodName(long methodId, int type, JfrReader reader, Dictionary<TypeValue> methodNames) {
        TypeValue result = methodNames.get(methodId);
        if (result != null) {
            return result;
        }

        MethodRef method = reader.methods.get(methodId);
        ClassRef cls = reader.classes.get(method.cls);
        byte[] className = reader.symbols.get(cls.name);
        byte[] methodName = reader.symbols.get(method.name);
        byte[] signature = reader.symbols.get(method.sig);

        if (className == null || className.length == 0) {
            String methodStr = new String(methodName, StandardCharsets.UTF_8);
            result = new OtherMethod(type == FRAME_KERNEL ? JFRMethodType.KERNEL : JFRMethodType.NATIVE, methodStr);
        } else {
            String classStr = new String(className, StandardCharsets.UTF_8).replace("/", ".");
            String methodStr = new String(methodName, StandardCharsets.UTF_8);
            String signatureStr = new String(signature, StandardCharsets.UTF_8);
            result = new JavaMethod(JFRMethodType.JAVA, classStr, methodStr, signatureStr, classStr + '.' + methodStr + signatureStr);
        }

        methodNames.put(methodId, result);
        return result;
    }

    public abstract ProfilerFileProto.MethodDictionarySlice.MethodDictionaryEntry getEntry(ProfileDictionary dictionary);

    public JFRMethodType getMethodType() {
        return methodType;
    }

    public enum JFRMethodType {
        JAVA('J'),
        KERNEL('K'),
        NATIVE('N');

        private final char prefix;

        JFRMethodType(char prefix) {
            this.prefix = prefix;
        }

        public char getPrefix() {
            return prefix;
        }
    }
}

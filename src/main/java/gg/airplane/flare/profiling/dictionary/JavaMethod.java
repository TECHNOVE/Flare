package gg.airplane.flare.profiling.dictionary;

import gg.airplane.flare.proto.ProfilerFileProto.MethodDictionarySlice;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class JavaMethod extends TypeValue {
    private static final Map<Character, String> PRIMITIVES;

    static {
        Map<Character, String> map = new HashMap<>();

        map.put('Z', "boolean");
        map.put('B', "byte");
        map.put('C', "char");
        map.put('S', "short");
        map.put('I', "int");
        map.put('J', "long");
        map.put('F', "float");
        map.put('D', "double");
        map.put('V', "void");

        PRIMITIVES = Collections.unmodifiableMap(map);
    }


    private final String classString;
    private final String methodStr;
    private final String signatureStr;

    public JavaMethod(JFRMethodType type, String classString, String methodStr, String signatureStr, String fullPath) {
        super(type);
        this.classString = Objects.requireNonNull(classString);
        this.methodStr = Objects.requireNonNull(methodStr);
        this.signatureStr = Objects.requireNonNull(signatureStr);
    }

    public String getClassString() {
        return classString;
    }

    public String getRawClass() {
        String className = this.classString;
        if (className.contains("$")) {
            className = className.substring(0, className.indexOf("$"));
        }
        return className;
    }

    public String getMethodStr() {
        return methodStr;
    }

    public String getSignatureStr() {
        return signatureStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaMethod javaMethod = (JavaMethod) o;
        return Objects.equals(classString, javaMethod.classString) && Objects.equals(methodStr, javaMethod.methodStr) && Objects.equals(signatureStr, javaMethod.signatureStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classString, methodStr, signatureStr);
    }

    @Override
    public String toString() {
        return this.getMethodType().getPrefix() + this.classString + "." + this.methodStr + this.signatureStr;
    }

    private MethodDictionarySlice.JavaDictionaryEntry.JavaTypeValue mapType(String unparsed, ProfileDictionary dictionary) {
        if (unparsed.charAt(unparsed.length() - 1) == ';') {
            unparsed = unparsed.substring(0, unparsed.length() - 1);
        }
        int array = 0;
        while (unparsed.charAt(0) == '[') {
            unparsed = unparsed.substring(1);
            array++;
        }

        char type = unparsed.charAt(0);

        if (PRIMITIVES.containsKey(type)) {
            return MethodDictionarySlice.JavaDictionaryEntry.JavaTypeValue.newBuilder()
              .setPrimitive(PRIMITIVES.get(type))
              .setArray(array)
              .build();
        }

        if (type == 'L') {
            String[] split = unparsed.substring(1).split("/");
            String className = split[split.length - 1];
            String packageName = split.length > 1 ? String.join(".", Arrays.copyOfRange(split, 0, split.length - 1)) : "";

            return MethodDictionarySlice.JavaDictionaryEntry.JavaTypeValue.newBuilder()
              .setJavaClassType(MethodDictionarySlice.JavaDictionaryEntry.JavaClass.newBuilder()
                .setPackageIndex(dictionary.getOrAddPackage(packageName))
                .setName(className)
                .build())
              .setArray(array)
              .build();
        }

        throw new RuntimeException("Unknown type for string: " + unparsed);
    }

    @Override
    public MethodDictionarySlice.MethodDictionaryEntry getEntry(ProfileDictionary dictionary) {
        MethodDictionarySlice.MethodDictionaryEntry.Builder builder = MethodDictionarySlice.MethodDictionaryEntry.newBuilder();
        MethodDictionarySlice.JavaDictionaryEntry.Builder entry = MethodDictionarySlice.JavaDictionaryEntry.newBuilder();

        int lambdaIndex = this.classString.indexOf("$$Lambda$");
        String lambdaText = "";
        String classString = this.classString;
        if (lambdaIndex > -1) {
            lambdaText = this.classString.substring(lambdaIndex);
            classString = this.classString.substring(0, lambdaIndex);
        }
        String[] split = classString.split("\\.");
        String className = split[split.length - 1] + lambdaText;
        String packageName = split.length > 1 ? String.join(".", Arrays.copyOfRange(split, 0, split.length - 1)) : "";

        entry.setJavaClass(MethodDictionarySlice.JavaDictionaryEntry.JavaClass.newBuilder()
          .setPackageIndex(dictionary.getOrAddPackage(packageName))
          .setName(className)
          .build());

        entry.setMethod(this.methodStr);

        // dumb parsing of the parameters
        String[] splitSig = this.signatureStr.substring(1).split("\\)");
        List<MethodDictionarySlice.JavaDictionaryEntry.JavaTypeValue> params = Arrays
          .stream(splitSig[0].split(";"))
          .filter(val -> val.length() > 0)
          .map(type -> {
              try {
                  return mapType(type, dictionary);
              } catch (Exception e) {
                  throw new RuntimeException("Failed to parse param " + type + " from " + this.signatureStr, e);
              }
          })
          .collect(Collectors.toList());

        entry.addAllParams(params);
        try {
            entry.setReturnType(mapType(splitSig[1], dictionary));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse return " + splitSig[1] + " from " + this.signatureStr, e);
        }

        builder.setJavaEntry(entry);
        return builder.build();
    }

}

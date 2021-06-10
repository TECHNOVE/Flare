package gg.airplane.flare.profiling.dictionary;

import gg.airplane.flare.proto.ProfilerFileProto;

public class OtherMethod extends TypeValue {

    private final String path;

    public OtherMethod(JFRMethodType methodType, String path) {
        super(methodType);
        this.path = path;
    }

    @Override
    public ProfilerFileProto.MethodDictionarySlice.MethodDictionaryEntry getEntry(ProfileDictionary dictionary) {
        return ProfilerFileProto.MethodDictionarySlice.MethodDictionaryEntry.newBuilder()
          .setOtherEntry(ProfilerFileProto.MethodDictionarySlice.OtherDictionaryEntry.newBuilder()
            .setType(ProfilerFileProto.MethodDictionarySlice.MethodType.valueOf(this.getMethodType().name()))
            .setPath(this.path)
            .build())
          .build();
    }

    @Override
    public String toString() {
        return "OtherMethod{" +
          "path='" + path + '\'' +
          '}';
    }
}

package co.technove.flare.internal.profiling;

public class InitializationException extends Exception {
    public InitializationException(String s) {
        super(s);
    }

    public InitializationException(Throwable throwable) {
        super(throwable);
    }

    public InitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

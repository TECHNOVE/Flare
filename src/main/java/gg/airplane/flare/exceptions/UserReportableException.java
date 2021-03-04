package gg.airplane.flare.exceptions;

public class UserReportableException extends Exception {
    private final String userError;

    public UserReportableException(String userError) {
        this(userError, userError);
    }

    public UserReportableException(String userError, String s) {
        super(s);
        this.userError = userError;
    }

    public UserReportableException(String userError, String s, Throwable throwable) {
        super(s, throwable);
        this.userError = userError;
    }

    public UserReportableException(String userError, Throwable throwable) {
        super(userError, throwable);

        this.userError = userError;
    }

    public UserReportableException(String userError, String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);

        this.userError = userError;
    }

    public String getUserError() {
        return userError;
    }
}

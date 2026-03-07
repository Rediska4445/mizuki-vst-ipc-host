package rf.ebanina.Player.AudioPlugins.VST;

public class VST3LoadException extends RuntimeException {
    public VST3LoadException() {
        super("VST-3 Load Exception");
    }

    public VST3LoadException(String message) {
        super(message);
    }

    public VST3LoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public VST3LoadException(Throwable cause) {
        super(cause);
    }

    protected VST3LoadException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

package rf.ebanina.vst;

public enum VstCommand {
    INIT_PLUGIN(0x01),
    CLOSE_PLUGIN(0x02),

    // Аудио процессинг
    PROCESS_AUDIO(0x10),

    // Редактор
    OPEN_EDITOR(0x20),
    CLOSE_EDITOR(0x21),

    // Состояние плагина
    SAVE_STATE(0x30),
    LOAD_STATE(0x31),

    // Параметры
    SET_PARAMETER(0x40),
    GET_PARAMETER(0x41),
    GET_PARAM_COUNT(0x42),
    GET_PARAM_INFO(0x43);

    private final int code;

    VstCommand(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static VstCommand fromCode(int code) {
        for (VstCommand cmd : values()) {
            if (cmd.code == code) {
                return cmd;
            }
        }
        return null;
    }

    public static boolean isValid(int code) {
        return fromCode(code) != null;
    }
}

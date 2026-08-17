package io.github.albert.extractorir;

public enum RemoteCommand {
    LIGHT_ON("Luz encendida", "110000001000", "C08"),
    LIGHT_OFF("Luz apagada", "110000100000", "C20"),
    FAN_1("Ventilador I", "110000000001", "C01"),
    FAN_2("Ventilador II", "110000000100", "C04"),
    FAN_3("Ventilador III", "110001000011", "C43"),
    FAN_OFF("Ventilador apagado", "110000010000", "C10");

    private final String displayName;
    private final String bits;
    private final String hexadecimal;

    RemoteCommand(String displayName, String bits, String hexadecimal) {
        this.displayName = displayName;
        this.bits = bits;
        this.hexadecimal = hexadecimal;
    }

    public String displayName() {
        return displayName;
    }

    public String bits() {
        return bits;
    }

    public String hexadecimal() {
        return hexadecimal;
    }

    public int[] pattern() {
        return IrProtocol.patternFor(bits);
    }
}

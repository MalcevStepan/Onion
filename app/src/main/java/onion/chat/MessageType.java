package onion.chat;

public enum MessageType {
	TEXT("msg", (byte)1), VIDEO("video", (byte)2), PHOTO("photo", (byte)3), AUDIO("audio", (byte)4);
	byte value;
	String type;

	MessageType(String type, byte value) {
		this.value = value;
		this.type = type;
	}

	String getType(){
		return type;
	}
	int getValue(){
		return value;
	}
	public static MessageType getEnum(String value) {
		for(MessageType v : values())
			if(v.getType().equalsIgnoreCase(value)) return v;
		throw new IllegalArgumentException();
	}
}

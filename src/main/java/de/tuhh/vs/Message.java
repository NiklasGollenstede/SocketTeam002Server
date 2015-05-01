package de.tuhh.vs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class Message {

	public final static byte headerLength = 12;
	public final static byte version = 0x01 - 0x80;
	public static final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
	
	public MessageType type;
	public ByteBuffer body = null;
	
	Message(MessageType type, ByteBuffer body) {
		this.type = type;
		this.body = body;
	}
	Message(Throwable e) {
		this.type = MessageType.ErrorCustom;
		if (e.getMessage() != null) {
			ByteBuffer buffer = ByteBuffer.allocate(e.getMessage().length() * 2);
			for (char c :e.getMessage().toCharArray()) {
				buffer.putChar(c);
			}
			this.body = buffer;
		}
	}
	
	
	
	public enum MessageType {
		ErrorCustom(0x40-0x80), // Es ist ein anderer Fehler aufgetreten. Body enthält Fehler-Details als String.
		InvalidProtocolVersion(0x41-0x80), // Fehler: Ungültige/nicht unterstützte Protokoll-Version
		InvalidPacketId(0x42-0x80), // Fehler: Ungültige Packet-ID. Eine Anfrage mit dieser Packet-ID wurde nicht gestellt.
		InvalidPacketType(0x43-0x80), // Fehler: Ungültiger Paket-Typ
		InvalidBodyLength(0x44-0x80), // Fehler: Ungültige Body-Länge (Erlaubt: 0 bis 2^63 - 1)
		InvalidBookingId(0x45-0x80), // Fehler: Ungültige/unbekannte Buchungs-ID.
		InvalidFieldLength(0x46-0x80), // Fehler: Ungültige Wert für ein Feld Zweck-Länge (Erlaubt: 0 bis 2^31 - 1)
		ErrorDeleteEntyChanged(0x47-0x80), // Fehler: Löschen abgebrochen, da Buchung auf Server verändert wurde.
		ErrorChangeEntyChanged(0x48-0x80), // Fehler: Bearbeiten abgebrochen, da Buchung auf Server verändert wurde.
		ErrorChangeIdMismatch(0x49-0x80), // Fehler: Bearbeiten abgebrochen, da IDs nicht übereinstimmen.
/*why?*/ErrorNoSuchMonth(0x4A-0x80), // Fehler: Monat existiert nicht.
		CallGetAll(0x81-0x80), // Fordert alle Buchungen im Haushaltsbuch an. Body ist leer, d.h., Body-Länge: 0.
		CallInsert(0x82-0x80), // Hinzufügen einer neuen Buchung. Body enthält Buchung im Format wie dargestellt in Abbildung 2. Die Buchungs-ID wird ignoriert.
		CallDelete(0x83-0x80), // Löschen einer Buchung. Body enthält Buchung im Format wie dargestellt in Abbildung 2. Nur wenn ID, Buchungstext und Buchungszeitpunkt übereinstimmen, löscht Server Eintrag auf Datenbank
		CallEdit(0x84-0x80), // Buchung bearbeiten. Body enthält zwei Buchungen. Zuerst der alte, zu überschreibene Eintrag, gefolgt vom neuen Eintrag. Die IDs beider Einträge müssen identisch sein.
		ResolveGetAll(0xA1-0x80), // Liste aller Einträge im Haushaltsbuch. Body enthält aneinandergehängte Buchungen im Format wie dargestellt in Abbildung 2.
		ResolveInsert(0xA2-0x80), // Bestätigung von Hinzufügen. Body enthält 4-byte Buchungs-ID.
		ResolveDelete(0xA3-0x80), // Bestätigung von Löschen. Body ist leer, d.h., Body-Länge: 0.
		ResolveEdit(0xA4-0x80); // Bestätigung von Bearbeiten. Body ist leer, d.h., Body-Länge: 0.

		private byte self;
		
		MessageType(int it) { this.self = (byte) it; }
		
		public byte get() { return self; }
		
		// returns the MessageType whos .get() returns 'it'
		public static MessageType from(byte it) throws IllegalArgumentException {
			for (MessageType item : MessageType.values()) {
				if (item.self == it) { return item; }
			}
			throw new IllegalArgumentException("Invalid message id: "+ it);
		}
		
		public String toString() {
			return this.name() +"("+ App.bytesToHex(new byte[]{(byte) (this.self + 128)}) +")";
		}
	}
	
	public static class ProtocolError extends RuntimeException {
		private static final long serialVersionUID = -7760110185107595555L;
		public MessageType messageType;
		ProtocolError(MessageType type) {
			super();
			this.messageType = type;
		}
	}
}


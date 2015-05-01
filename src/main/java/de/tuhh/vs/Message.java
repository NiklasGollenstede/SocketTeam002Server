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
		ErrorCustom(0x40-0x80), // Es ist ein anderer Fehler aufgetreten. Body enth�lt Fehler-Details als String.
		InvalidProtocolVersion(0x41-0x80), // Fehler: Ung�ltige/nicht unterst�tzte Protokoll-Version
		InvalidPacketId(0x42-0x80), // Fehler: Ung�ltige Packet-ID. Eine Anfrage mit dieser Packet-ID wurde nicht gestellt.
		InvalidPacketType(0x43-0x80), // Fehler: Ung�ltiger Paket-Typ
		InvalidBodyLength(0x44-0x80), // Fehler: Ung�ltige Body-L�nge (Erlaubt: 0 bis 2^63 - 1)
		InvalidBookingId(0x45-0x80), // Fehler: Ung�ltige/unbekannte Buchungs-ID.
		InvalidFieldLength(0x46-0x80), // Fehler: Ung�ltige Wert f�r ein Feld Zweck-L�nge (Erlaubt: 0 bis 2^31 - 1)
		ErrorDeleteEntyChanged(0x47-0x80), // Fehler: L�schen abgebrochen, da Buchung auf Server ver�ndert wurde.
		ErrorChangeEntyChanged(0x48-0x80), // Fehler: Bearbeiten abgebrochen, da Buchung auf Server ver�ndert wurde.
		ErrorChangeIdMismatch(0x49-0x80), // Fehler: Bearbeiten abgebrochen, da IDs nicht �bereinstimmen.
/*why?*/ErrorNoSuchMonth(0x4A-0x80), // Fehler: Monat existiert nicht.
		CallGetAll(0x81-0x80), // Fordert alle Buchungen im Haushaltsbuch an. Body ist leer, d.h., Body-L�nge: 0.
		CallInsert(0x82-0x80), // Hinzuf�gen einer neuen Buchung. Body enth�lt Buchung im Format wie dargestellt in Abbildung 2. Die Buchungs-ID wird ignoriert.
		CallDelete(0x83-0x80), // L�schen einer Buchung. Body enth�lt Buchung im Format wie dargestellt in Abbildung 2. Nur wenn ID, Buchungstext und Buchungszeitpunkt �bereinstimmen, l�scht Server Eintrag auf Datenbank
		CallEdit(0x84-0x80), // Buchung bearbeiten. Body enth�lt zwei Buchungen. Zuerst der alte, zu �berschreibene Eintrag, gefolgt vom neuen Eintrag. Die IDs beider Eintr�ge m�ssen identisch sein.
		ResolveGetAll(0xA1-0x80), // Liste aller Eintr�ge im Haushaltsbuch. Body enth�lt aneinandergeh�ngte Buchungen im Format wie dargestellt in Abbildung 2.
		ResolveInsert(0xA2-0x80), // Best�tigung von Hinzuf�gen. Body enth�lt 4-byte Buchungs-ID.
		ResolveDelete(0xA3-0x80), // Best�tigung von L�schen. Body ist leer, d.h., Body-L�nge: 0.
		ResolveEdit(0xA4-0x80); // Best�tigung von Bearbeiten. Body ist leer, d.h., Body-L�nge: 0.

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


package de.tuhh.vs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class Message {

	public final static byte headerLength = 12;
	public final static byte version = 0x01;
	public static final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
	public static Charset charset = Charset.forName("UTF-8");
	
	public MessageType type;
	public ByteBuffer body = null;
	
	/**
	 * Message Constructor
	 * Creates a message by type and message body
	 * @param	type	The message type
	 * @param	body	The actual message(message body)
	 */
	Message(MessageType type, ByteBuffer body) {
		this.type = type;
		this.body = body;
	}
	/**
	 * Message Constructor
	 * Creates a message of type ErrorCustom and copies e.getMessage() into the body, if any
	 * @param e a Throwable
	 */
	Message(Throwable e) {
		this.type = MessageType.ErrorCustom;
		if (e.getMessage() != null) {
			byte[] bytes = e.getMessage().getBytes(Message.charset);
			this.body = ByteBuffer.allocate(bytes.length);
			this.body.put(bytes);
		}
	}
	
	
	/**
	 * All the valid MessageTypes
	 */
	public enum MessageType {
		ErrorCustom(0x40), // Es ist ein anderer Fehler aufgetreten. Body enth�lt Fehler-Details als String.
		InvalidProtocolVersion(0x41), // Fehler: Ung�ltige/nicht unterst�tzte Protokoll-Version
		InvalidPacketId(0x42), // Fehler: Ung�ltige Packet-ID. Eine Anfrage mit dieser Packet-ID wurde nicht gestellt.
		InvalidPacketType(0x43), // Fehler: Ung�ltiger Paket-Typ
		InvalidBodyLength(0x44), // Fehler: Ung�ltige Body-L�nge (Erlaubt: 0 bis 2^63 - 1)
		InvalidBookingId(0x45), // Fehler: Ung�ltige/unbekannte Buchungs-ID.
		InvalidFieldLength(0x46), // Fehler: Ung�ltige Wert f�r ein Feld Zweck-L�nge (Erlaubt: 0 bis 2^31 - 1)
		ErrorDeleteEntyChanged(0x47), // Fehler: L�schen abgebrochen, da Buchung auf Server ver�ndert wurde.
		ErrorChangeEntyChanged(0x48), // Fehler: Bearbeiten abgebrochen, da Buchung auf Server ver�ndert wurde.
		ErrorChangeIdMismatch(0x49), // Fehler: Bearbeiten abgebrochen, da IDs nicht �bereinstimmen.
/*why?*/ErrorNoSuchMonth(0x4A), // Fehler: Monat existiert nicht.
		CallGetAll(0x81), // Fordert alle Buchungen im Haushaltsbuch an. Body ist leer, d.h., Body-L�nge: 0.
		CallInsert(0x82), // Hinzuf�gen einer neuen Buchung. Body enth�lt Buchung im Format wie dargestellt in Abbildung 2. Die Buchungs-ID wird ignoriert.
		CallDelete(0x83), // L�schen einer Buchung. Body enth�lt Buchung im Format wie dargestellt in Abbildung 2. Nur wenn ID, Buchungstext und Buchungszeitpunkt �bereinstimmen, l�scht Server Eintrag auf Datenbank
		CallEdit(0x84), // Buchung bearbeiten. Body enth�lt zwei Buchungen. Zuerst der alte, zu �berschreibene Eintrag, gefolgt vom neuen Eintrag. Die IDs beider Eintr�ge m�ssen identisch sein.
		ResolveGetAll(0xA1), // Liste aller Eintr�ge im Haushaltsbuch. Body enth�lt aneinandergeh�ngte Buchungen im Format wie dargestellt in Abbildung 2.
		ResolveInsert(0xA2), // Best�tigung von Hinzuf�gen. Body enth�lt 4-byte Buchungs-ID.
		ResolveDelete(0xA3), // Best�tigung von L�schen. Body ist leer, d.h., Body-L�nge: 0.
		ResolveEdit(0xA4); // Best�tigung von Bearbeiten. Body ist leer, d.h., Body-L�nge: 0.

		private byte self;
		
		/**
		 * MessageType Constructor
		 * Creates a message with the given message id
		 * @param	it	The message id
		 */
		MessageType(int it) { this.self = (byte) it; }
		
		/**
		 * Returns the message id
		 * @return	self	The message id
		 */
		public byte get() { return self; }
		
		/**
		 * Returns the MessageType whos .get() method returns the value 'it'
		 * @param	it	The message id of the wanted MessageType
		 * @return		The wanted MessageType
		 * @throws	IllegalArgumentException	Thrown for invalid message id
		 */
		public static MessageType from(byte it) throws IllegalArgumentException {
			for (MessageType item : MessageType.values()) {
				if (item.self == it) { return item; }
			}
			throw new IllegalArgumentException("Invalid message id: "+ it);
		}
		
		/**
		 * Returns a string representation of the MessageType
		 * @return	The string representing the MessageType
		 */
		@Override
		public String toString() {
			return this.name() +"("+ App.bytesToHex(new byte[]{(byte) (this.self + 128)}) +")";
		}
	}
	
	/**
	 * This calss embodying the exception of an ProtocolError(in case of invalid lengths)
	 */
	public static class ProtocolError extends RuntimeException {
		private static final long serialVersionUID = -7760110185107595555L;
		public MessageType messageType;
		/**
		 * ProtocolError Constructor 
		 * @param	type	The exception message
		 */
		ProtocolError(MessageType type) {
			super();
			this.messageType = type;
		}
	}
}


package de.tuhh.vs;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import de.tuhh.vs.Message.MessageType;
import de.tuhh.vs.Message.ProtocolError;
import de.tuhh.vs.samples.common.db.PersistentObject;

public class Booking extends PersistentObject {
	private static final long serialVersionUID = 158803217403953213L;
	private static Charset charset = Charset.forName("UTF-8");
	private static byte[] spaces = new String("\0\0\0\0\0\0\0\0"/*8*/).getBytes(charset);

	private int id;
	private byte[] purpose;
	private double amount;
	private long timestamp;

	public Booking(String purpose, double amount) {
		this(0, purpose, amount, System.currentTimeMillis());
	}

	public Booking(int i, String purpose, double amount) {
		this(i, purpose, amount, System.currentTimeMillis());
	}

	public Booking(int id, String purpose, double amount, long timestamp) {
		this.id = id;
		byte[] chars = purpose.getBytes(charset);
		this.purpose = new byte[chars.length + (8 * (chars.length % 8 == 0 ? 0 : 1) - (chars.length % 8))];
		System.arraycopy(chars, 0, this.purpose, 0, chars.length);
		System.arraycopy(Booking.spaces, 0, this.purpose, chars.length, this.purpose.length - chars.length);
		this.amount = amount;
		this.timestamp = timestamp;
	}

	// reads a Booking from the buffer at its position and increments it
	public Booking(ByteBuffer buffer) throws ProtocolError {
		this.id = buffer.getInt();
		int length = buffer.getInt() * 8;
		if (length < 0) {
			throw new ProtocolError(MessageType.InvalidFieldLength);
		}
		this.purpose = new byte[length];
		buffer.get(this.purpose);
		this.amount = buffer.getDouble();
		this.timestamp = buffer.getLong();
	}

	// returns the space this needs when written to a buffer
	public int size() {
		return 4 + 4 + (this.purpose.length) + 8 + 8;
	}

	// writes a Booking to the buffer at its position and increments it
	public void write(ByteBuffer buffer) {
		buffer.putInt(this.id);
		buffer.putInt((this.purpose.length) / 8);
		buffer.put(this.purpose);
		buffer.putDouble(this.amount);
		buffer.putLong(this.timestamp);
	}

	// ^= (this, that) -> Booking[@@members].every(member -> this[member].equals(that[member]))
	public boolean equals(Booking that) {
		return this.id == that.id
			&& Arrays.equals(this.purpose, that.purpose)
			&& this.amount == that.amount
			&& this.timestamp == that.timestamp;
	}

	// PersistentObject
	public void setKey(final int key) { this.id = key; }
	public int getKey() { return id; }

	public String toString() {
		return "Booking{ "
				+"id: "+ this.id +", "
				+"purpose: \""+ new String(this.purpose, charset) +"\"(" + this.purpose.length +"), "
				+"amount: "+ this.amount +", "
				+"timestamp: "+ this.timestamp +", "
			+"}";
	}
}

package de.tuhh.vs;

import java.nio.ByteBuffer;
import java.util.Arrays;

import de.tuhh.vs.Message.MessageType;
import de.tuhh.vs.Message.ProtocolError;
import de.tuhh.vs.samples.common.db.PersistentObject;

public class Booking extends PersistentObject {
	private static final long serialVersionUID = 158803217403953213L;
	private static byte[] spaces = new String("\0\0\0\0\0\0\0\0"/*8*/).getBytes(Message.charset);

	private int id;
	private byte[] purpose;
	private double amount;
	private long timestamp;

	/**
	 * Booking-Constructor
	 * Creates a new Booking-Object given a certain purpose and amount.
	 * The id is set to 0 and the timestamp to the current time
	 * 
	 * @param purpose	The purpose of the booking on form of a string
	 * @param amount	The amount of the booking(so the costs)
	 */
	public Booking(String purpose, double amount) {
		this(0, purpose, amount, System.currentTimeMillis());
	}

	/**
	 * Booking-Constructor
	 * Creates a new Booking-Object given a certain id, purpose and amount.
	 * The timestamp is set to the current time
	 * 
	 * @param i			The id of the booking
	 * @param purpose	The purpose of the booking on form of a string
	 * @param amount	The amount of the booking(so the costs)
	 */
	public Booking(int i, String purpose, double amount) {
		this(i, purpose, amount, System.currentTimeMillis());
	}

	/**
	 * Booking-Constructor
	 * Creates a new Booking-Object given a certain id, purpose, amount and timestamp
	 * 
	 * @param id		The id of the booking
	 * @param purpose	The purpose of the booking on form of a string
	 * @param amount	The amount of the booking(so the costs)
	 * @param timestamp	The timestamp of the booking(time where it is made)
	 */
	public Booking(int id, String purpose, double amount, long timestamp) {
		this.id = id;
		byte[] chars = purpose.getBytes(Message.charset);
		this.purpose = new byte[chars.length + (8 * (chars.length % 8 == 0 ? 0 : 1) - (chars.length % 8))];
		System.arraycopy(chars, 0, this.purpose, 0, chars.length);
		System.arraycopy(Booking.spaces, 0, this.purpose, chars.length, this.purpose.length - chars.length);
		this.amount = amount;
		this.timestamp = timestamp;
	}

	/**
	 * Reads a booking from the a byte-buffer at the current position of the buffer 
	 * and also increments the buffers position
	 * @param	buffer			The byte-buffer from which to read 
	 * @throws	ProtocolError	Exception thrown in case of invalid length for the body(only positive values allowed)
	 */
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

	/**
	 * Returns the size of this booking needed to write to the byte-buffer(in bytes)
	 * @return	The size of the booking in bytes when sending to the byte-buffer
	 */
	public int size() {
		return 4 + 4 + (this.purpose.length) + 8 + 8;
	}

	/**
	 * Writes a booking into the byte-buffer at the current position of the buffer
	 * and also increments the buffer position
	 * @param	buffer	The byte-buffer to which to write
	 */
	public void write(ByteBuffer buffer) {
		buffer.putInt(this.id);
		buffer.putInt((this.purpose.length) / 8);
		buffer.put(this.purpose);
		buffer.putDouble(this.amount);
		buffer.putLong(this.timestamp);
	}

	/**
	 * Checks whether this booking equals another passed booking in the sense
	 * of having the same 'id', 'purpose', 'amount' and 'timestamp'
	 * @param	that	The booking against which to check equality
	 * @return			A boolean whether this booking equals that passed one
	 */
	public boolean equals(Booking that) {
		return this.id == that.id
			&& Arrays.equals(this.purpose, that.purpose)
			&& this.amount == that.amount
			&& this.timestamp == that.timestamp;
	}

	/**
	 * Sets the booking-id to the passed value
	 * @param	key		The new value for the id
	 */
	public void setKey(final int key) { this.id = key; }
	
	/**
	 * Returns the id of the booking
	 * @return	The id of the booking
	 */
	public int getKey() { return id; }

	/**
	 * Returns a string which contains the information of the booking
	 * @return	A string representation of the booking
	 */
	@Override
	public String toString() {
		return "Booking{ "
				+"id: "+ this.id +", "
				+"purpose: \""+ new String(this.purpose, Message.charset) +"\"(" + this.purpose.length +"), "
				+"amount: "+ this.amount +", "
				+"timestamp: "+ this.timestamp +", "
			+"}";
	}
}

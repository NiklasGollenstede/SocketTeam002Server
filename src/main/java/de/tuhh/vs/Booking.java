package de.tuhh.vs;

import java.nio.ByteBuffer;

import de.tuhh.vs.Message.MessageType;
import de.tuhh.vs.Message.ProtocolError;
import de.tuhh.vs.samples.common.db.PersistentObject;

public class Booking extends PersistentObject {
	private static final long serialVersionUID = 158803217403953213L;
	
	private int id;
	private String purpose;
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
		this.purpose = purpose;
		while (this.purpose.getBytes().length % 8 != 0) {
			this.purpose += " ";
		}
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
		byte[] bytes = new byte[length];
		buffer.get(bytes);
		this.purpose = new String(bytes);
		this.amount = buffer.getDouble();
		this.timestamp = buffer.getLong();
	}
	
	// returns the space this needs when wirten to a buffer
	public int size() {
		return 4 + 4 + (this.purpose.getBytes().length) + 8 + 8;
	}
	
	// writes a Booking to the buffer at its position and increments it
	public void write(ByteBuffer buffer) {
		buffer.putInt(this.id);
		byte[] bytes = this.purpose.getBytes();
		buffer.putInt((bytes.length) / 8);
		buffer.put(bytes);
		buffer.putDouble(this.amount);
		buffer.putLong(this.timestamp);
	}
	
	// ^= (this, that) -> Booking.@@members.every(member -> this[member].equals(that[member]))
	public boolean equals(Booking that) {
		return this.id == that.id
			&& this.purpose.equals(that.purpose)
			&& this.amount == that.amount
			&& this.timestamp == that.timestamp;
	}

	// PersistentObject
	public void setKey(final int key) { this.id = key; }
	public int getKey() { return id; }
	
	public String toString() {
		return "Booking{ "
				+"id: "+ this.id +", "
				+"purpose: \""+ this.purpose +"\"("+ this.purpose.length() +"), "
				+"amount: "+ this.amount +", "
				+"timestamp: "+ this.timestamp +", "
			+"}";
	}
}

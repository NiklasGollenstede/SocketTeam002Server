package de.tuhh.vs;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import de.tuhh.vs.Message.MessageType;
import de.tuhh.vs.Message.ProtocolError;
import de.tuhh.vs.samples.common.db.DBException;
import de.tuhh.vs.samples.common.db.FlatDB;

public abstract class Handler {
	
	/**
	 * 
	 * @param 	dbDirectory	The path to the database directory
	 * @return	A handler function that closures around the database and can be passed to the server
	 * @throws	DBException	Thrown
	 */
	public static final BiConsumer<Message, Consumer<Message>> getHandler(String dbDirectory) throws DBException {

		final FlatDB<Booking> db = new FlatDB<Booking>(dbDirectory);
		
		/**
		 * @param	request		Message from the client
		 * @param	response	Consumer of Message that can be called once to respond to the request
		 */
		return (request, response) -> {

			try {
				switch (request.type) {
					case ErrorCustom:
					case InvalidProtocolVersion:
					case InvalidPacketId:
					case InvalidPacketType:
					case InvalidBodyLength:
					case InvalidBookingId:
					case InvalidFieldLength:
					case ErrorDeleteEntyChanged:
					case ErrorChangeEntyChanged:
					case ErrorChangeIdMismatch:
					case ErrorNoSuchMonth: {
						int length = request.body != null ? request.body.limit() : 0;
						System.out.println("Server recieved error message, "+ request.type +":\n"+
								(length != 0 ? "\t("+ length +")"+ App.bytesToHex(request.body.array()) : "<no body>"));
					} break;
					case CallGetAll: {
						int length =  0;
						for (Booking booking : db) { length += booking.size(); }
						ByteBuffer buffer = ByteBuffer.allocate(length);
						buffer.order(Message.byteOrder);
						for (Booking booking : db) { booking.write(buffer); }
						String print = ("Server bookings: [\n");
							for (Booking booking : db) { print += ("\t"+ booking +",\n"); }
						System.out.println(print +"]");
						response.accept(new Message(MessageType.ResolveGetAll, buffer));
					} break;
					case CallInsert: {
						Booking booking = new Booking(request.body);
						db.insert(booking);
						ByteBuffer buffer = ByteBuffer.allocate(4);
						buffer.order(Message.byteOrder);
						buffer.putInt(booking.getKey());
						System.out.println("Server inserted "+ booking);
						response.accept(new Message(MessageType.ResolveInsert, buffer));
					} break;
					case CallDelete: {
						Booking booking = new Booking(request.body);
						if (booking.equals(db.get(booking.getKey()))) {
							db.delete(booking);
							System.out.println("Server deleted "+ booking);
							response.accept(new Message(MessageType.ResolveDelete, null));
						} else {
							response.accept(new Message(MessageType.ErrorDeleteEntyChanged, null));
						}
					} break;
					case CallEdit: {
						Booking old = new Booking(request.body);
						Booking now = new Booking(request.body);
						Booking current;
						if (old == null || now == null || old.getKey() != now.getKey()
							|| (current = db.get(old.getKey())) == null
						) {
							response.accept(new Message(MessageType.ErrorChangeIdMismatch, null));
						} else if (!old.equals(current)) {
							response.accept(new Message(MessageType.ErrorChangeEntyChanged, null));
						} else {
							db.update(now);
							System.out.println("Server updated "+ old +"\nto "+ now);
							response.accept(new Message(MessageType.ResolveEdit, null));
						}
					} break;
					case ResolveGetAll:
					case ResolveInsert:
					case ResolveDelete:
					case ResolveEdit: {
						response.accept(new Message(MessageType.InvalidPacketType, null));
					} break;
				}
			} catch(ProtocolError e) {
				response.accept(new Message(e.messageType, null));
			} catch (Throwable e) {
				response.accept(new Message(e));
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		};
	}
}

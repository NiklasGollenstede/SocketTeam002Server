package de.tuhh.vs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import de.tuhh.vs.Message.MessageType;
import de.tuhh.vs.Message.ProtocolError;



public class Client implements AutoCloseable {
	
	private static short messageCounter = 0;
	
	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private Thread thread;
	private Map<Short, CompletableFuture<Object>> futures = new HashMap<Short, CompletableFuture<Object>>();
	
	public Client(int port) throws UnknownHostException, IOException {
		this.socket = new Socket(InetAddress.getLocalHost(), port);
		this.in = new DataInputStream(socket.getInputStream());
		this.out = new DataOutputStream(socket.getOutputStream());
		this.thread = new Thread(() -> {
			try {
				byte version;
				MessageType type;
				ByteBuffer body;
				do {
					System.out.println("Client start read");

					ByteBuffer header = ByteBuffer.allocate(Message.headerLength);
					header.order(Message.byteOrder);
					
					
					for (
						int read = 0;
						read < Message.headerLength;
						read += in.read(header.array(), read, Message.headerLength - read)
					) {
						if (read < 0) {
							throw new Exception("Unable to read header");
						}
					}
					
					version = header.get();
					short messageId = header.getShort();
					
					try {
						// check protocol version
						if (version != Message.version) {
							throw new Exception("Version is not Message.version");
						}
		
						// parse packet type
						try {
							type = MessageType.from(header.get());
						} catch (IllegalArgumentException e) {
							throw new Exception("Recived packet of invalid type");
						}
						
						// read body of correct length
						try {
							body = ByteBuffer.allocate((int) header.getLong());
							body.order(Message.byteOrder);
							in.read(body.array());
						} catch (IllegalArgumentException e) {
							throw new Exception("Recived packet of invalid length");
						}
						
						System.out.println("Client recieved "+ messageId +": "+ type +":\n"+
								"\t("+ body.array().length +")"+ App.bytesToHex(body.array()));
		
						switch(type) { // TODO check expected types
							case ResolveGetAll: {
								Vector<Booking> all = new Vector<Booking>();
								while (body.position() < body.limit()) {
									all.add(new Booking(body));
								}
								this.futures.get(messageId).complete(all);
							} break;
							case ResolveInsert: {
								if (body.limit() != 4) { throw new Exception("Recived packet of unexpedted length"); }
								this.futures.get(messageId).complete(body.getInt());
							} break;
							case ResolveDelete: {
								this.futures.get(messageId).complete(null);
							} break;
							case ResolveEdit: {
								this.futures.get(messageId).complete(null);
							} break;
							case ErrorDeleteEntyChanged:
							case ErrorChangeEntyChanged:
							case ErrorChangeIdMismatch: {
								this.futures.get(messageId).completeExceptionally(new ProtocolError(type));
							} break;
							default: {
								throw new Exception("Recived packet of unexpedted type "+ type);
							}
						}
					} catch (Throwable e) {
						System.out.println("Response error: "+ e.getMessage());
						e.printStackTrace();
					} finally {
						this.futures.get(messageId).cancel(false);
						this.futures.remove(messageId);
						System.out.println("removed future for "+ messageId);
					}
					
				} while (true);
			} catch(Throwable e) {
				System.out.println("Client ecountered critical error: "+ e.getMessage());
			} finally {
				try {
					this.in.close();
					this.in = null;
					this.out.close();
					this.out = null;
					this.socket.close();
					this.socket = null;
					for (CompletableFuture<Object> f : this.futures.values()) {
						f.cancel(false);
					}
					this.futures = null;
					this.thread = null;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		this.thread.start();
	}
	
	public void close() {
		try {
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void sendMessage(short messageId, Message resquest) throws IOException {
		assert(resquest.body == null || resquest.body.limit() == resquest.body.array().length);
		int length = resquest.body != null ? resquest.body.limit() : 0;
		System.out.println("Client requests "+ messageId +": "+ resquest.type +":\n"+
				(length != 0 ? "\t("+ length +")"+ App.bytesToHex(resquest.body.array()) : "\t<no body>"));
		ByteBuffer buffer = ByteBuffer.allocate(length + Message.headerLength);
		buffer.order(Message.byteOrder);
		buffer.put((byte) Message.version);
		buffer.putShort(messageId);
		buffer.put(resquest.type.get());
		buffer.putLong(length);
		if (resquest.body != null) {
			buffer.put(resquest.body.array());
		}
		this.out.write(buffer.array());
	}
	
	public CompletableFuture<Object> getAll() throws IOException {
		short messageId = ++Client.messageCounter;
		CompletableFuture<Object> future = new CompletableFuture<Object>();
		this.futures.put(messageId, future);
		this.sendMessage(messageId, new Message(MessageType.CallGetAll, null));
		return future;
	}
	
	public CompletableFuture<Object> insert(Booking booking) throws IOException {
		short messageId = ++Client.messageCounter;
		CompletableFuture<Object> future = new CompletableFuture<Object>();
		this.futures.put(messageId, future);
		ByteBuffer buffer = ByteBuffer.allocate(booking.size());
		buffer.order(Message.byteOrder);
		booking.write(buffer);
		this.sendMessage(messageId, new Message(MessageType.CallInsert, buffer));
		return future;
	}
	
	public CompletableFuture<Object> delete(Booking booking) throws IOException {
		short messageId = ++Client.messageCounter;
		CompletableFuture<Object> future = new CompletableFuture<Object>();
		this.futures.put(messageId, future);
		ByteBuffer buffer = ByteBuffer.allocate(booking.size());
		buffer.order(Message.byteOrder);
		booking.write(buffer);
		this.sendMessage(messageId, new Message(MessageType.CallDelete, buffer));
		return future;
	}
	
	public CompletableFuture<Object> edit(Booking old, Booking now) throws IOException {
		short messageId = ++Client.messageCounter;
		CompletableFuture<Object> future = new CompletableFuture<Object>();
		this.futures.put(messageId, future);
		ByteBuffer buffer = ByteBuffer.allocate(old.size() + now.size());
		buffer.order(Message.byteOrder);
		old.write(buffer);
		now.write(buffer);
		this.sendMessage(messageId, new Message(MessageType.CallEdit, buffer));
		return future;
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		int port = 8080;
		try (
			Client one = new Client(port);
			Client two = new Client(port);
		) {
			
			one.insert(new Booking("Toast", 0.70));
			two.insert(new Booking("Butter", 0.70));
			
			Booking klopse = new Booking("Klopse", 3.20);
			klopse.setKey((int) one.insert(klopse).get());
			//System.exit(0);
			System.out.println("Klopse: "+ klopse);
			
			two.edit(klopse, new Booking(klopse.getKey(), "Mehr klopsööö", Double.MAX_VALUE)).get();
			
			Future<Object> fail = one.edit(klopse, new Booking(klopse.getKey(), "Keine Klopse", 0));
			
			try {
				fail.get();
			} catch (ExecutionException e) {
				System.out.println("Test successfully didn't change: "+
						((ProtocolError) e.getCause()).messageType.name());
			}
			
			String print = "Test Bookings [\n";
			for (Booking booking : (Vector<Booking>) one.getAll().get()) {
				print += ("\t"+ booking +",\n");
			}
			System.out.println(print +"]");
			

			for (Booking booking : (Vector<Booking>) two.getAll().get()) {
				two.delete(booking);
			}
			
			print = "Test Bookings [\n";
			for (Booking booking : (Vector<Booking>) two.getAll().get()) {
				print += ("\t"+ booking +",\n");
			}
			System.out.println(print +"]");
			
			System.out.println("Test database cleared");
		} catch (Exception e) {
			System.out.println("Test threw: "+ e.getMessage());
			e.printStackTrace();
		}
	}
}

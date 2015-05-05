package de.tuhh.vs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import de.tuhh.vs.Message.MessageType;

public class Server implements AutoCloseable {
	
	private Thread thread;
	private ServerSocket socket;
	private CompletableFuture<Exception> done;
	private BiConsumer<Message, Consumer<Message>> handler;
	private Vector<Socket> clients;
	
	
	/**
	 * Constructs a message packet with the passed message-id and response
	 * and writes it to the passed outputstream
	 * 
	 * @param	out			The outputstream into which to write the message 
	 * @param	messageId	The id of the message to write
	 * @param	response	The response-message itself
	 * @throws	IOException	Thrown if an I/O error occurs(while writing to the outputstream)
	 */
	private void sendResponse(DataOutputStream out, short messageId, Message response) throws IOException {
		int length = response.body != null ? response.body.limit() : 0;
		System.out.println("Server responding "+ messageId +": "+ response.type +":\n"+
				(length != 0 ? "\t("+ length +")"+ App.bytesToHex(response.body.array()) : "\t<no body>"));
		ByteBuffer buffer = ByteBuffer.allocate(length + Message.headerLength);
		buffer.order(Message.byteOrder);
		buffer.put((byte) Message.version);
		buffer.putShort(messageId);
		buffer.put(response.type.get());
		buffer.putLong(length);
		if (response.body != null) {
			buffer.put(response.body.array());
		}
		out.write(buffer.array());
	}
	/**
	 * Constructs a message packet using the passed message-id and message type, but with empty body
	 * Afterwards it writes it to the passed outputstream
	 * 
	 * @param	out			The outputstream into which to write the message 
	 * @param	messageId	The id of the message to write
	 * @param	type		The message type
	 * @throws	IOException	Thrown if an I/O error occurs(while writing to the outputstream)
	 */
	private void sendResponse(DataOutputStream out, short messageId, MessageType type) throws IOException {
		sendResponse(out, messageId, new Message(type, null));
	}
	
	/**
	 * Handles the communication with a client synchronously
	 * @param	client		The client that has to be handled
	 * @throws	IOException	Thrown if an I/O error occurs(while creating the data-input- and data-output-stream)
	 */
	private void handleClient(Socket client) throws IOException {
		try (
			DataInputStream in = new DataInputStream(client.getInputStream());
			DataOutputStream out = new DataOutputStream(client.getOutputStream());
		) {
			byte version;
			MessageType type;
			ByteBuffer body;
			do {
				System.out.println("Server start read");

				// read header
				ByteBuffer header = ByteBuffer.allocate(Message.headerLength);
				header.order(Message.byteOrder);
				for (
					int read = 0;
					read < Message.headerLength;
					read += in.read(header.array(), read, Message.headerLength - read)
				) {
					if (read < 0) {
						System.out.println("Server unable to read header, closing connection");
						return;
					}
				}
				
				version = header.get();
				final short messageId = header.getShort();

				// check protocol version
				if (version != Message.version) {
					sendResponse(out, messageId, MessageType.InvalidProtocolVersion);
					in.skip(Integer.MAX_VALUE);
					continue;
				}

				// parse packet type
				try {
					type = MessageType.from(header.get());
				} catch (IllegalArgumentException e) {
					sendResponse(out, messageId, MessageType.InvalidPacketType);
					in.skip(Integer.MAX_VALUE);
					continue;
				}
				
				// read body of correct length
				try {
					int length = (int) header.getLong();
					body = ByteBuffer.allocate(length);
					body.order(Message.byteOrder);
					if (length != in.read(body.array())) {
						throw new IllegalArgumentException();
					}
				} catch (IllegalArgumentException e) {
					sendResponse(out, messageId, MessageType.InvalidBodyLength);
					in.skip(Integer.MAX_VALUE);
					continue;
				}
				
				System.out.println("Server recieved "+ messageId +": "+ type +":\n"+
						"\t("+ body.array().length +")"+ App.bytesToHex(body.array()));

				// send response asynchronously
				CompletableFuture<Object> resolved = new CompletableFuture<Object>();

				// let callback handle the message
				this.handler.accept(new Message(type, body), (Message message) -> {
					try {
						if (resolved.isDone()) {
							throw new RuntimeException("response has already been sent");
						}
						this.sendResponse(out, messageId, message);
						resolved.complete(null);
					} catch (Exception e) {
						System.out.println("Server failed to respond to packet "+ messageId +": "+ e.getMessage());
					}
				});
				
			} while (!Thread.currentThread().isInterrupted());
		} finally {
			client.close();
		}
	}
	
	/**
	 * Server Constructor
	 * Creates a server that asynchronously accepts connections to 'port'
	 * and passes the type and body of each incoming message to the handler function,
	 * which can then invoke its second argument with its response Message
	 * @param	port	The port to which the server listens
	 * @param	handler	function which will be called for each incoming message with the message and a response handler as arguments.
	 * 					The response handler can be used once to respond to this message
	 * @throws	InterruptedException	Thrown if an Exception occurs while constructing the server
	 */
	public Server(int port, BiConsumer<Message, Consumer<Message>> handler) throws InterruptedException {
		this.handler = handler;
		this.done = new CompletableFuture<Exception>();
		this.clients = new Vector<Socket>();
		CompletableFuture<Object> wait = new CompletableFuture<Object>();
		this.thread = new Thread(() -> {
			try (
				ServerSocket server = new ServerSocket(port);
			) {
				this.socket = server;
				wait.complete(null);
				do {
					Socket client = server.accept();
					Thread thread = new Thread(() -> {
						System.out.println("Server client connected");
						try {
							this.handleClient(client);
						} catch (EOFException | SocketException e) {
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							System.out.println("Server client disconnedted");
							this.clients.remove(client);
						}
					});
					this.clients.add(client);
					thread.start();
				} while (!Thread.currentThread().isInterrupted());
			} catch (Exception e) {
				this.done.complete(e);
				wait.completeExceptionally(e);
				this.close();
			} finally {
				this.socket = null;
				this.done.cancel(false);
				wait.cancel(false);
				System.out.println("Server exit");
			}
        });
		this.thread.start();
		try {
			wait.get(); // wait for server to start before returning
		} catch (ExecutionException e) { }
	}
	
	// 
	/**
	 * Closes the server and interrupts all associates threads
	 */
	public void close() {
		if (this.thread == null) { return; }
		this.softClose();
		for (Socket client : this.clients) {
			try {
				client.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Closes only the server thread and keeps the client connections alive
	 */
	public void softClose() {
		if (this.thread == null) { return; }
		System.out.println("Server interrupt");
		if (this.socket != null) try {
			this.socket.close();
		} catch (IOException e) {
			throw new RuntimeException(e +": "+ e.getMessage(), e);
		}
		if (this.thread != null) { this.thread.interrupt(); }
		this.done.cancel(false);
		this.thread =  null;
	}
	
	/**
	 * Blocks until the server thread is done and then throws any exceptions which the server thread caused
	 * @throws	Exception	Thrown if the server thread threw
	 */
	public void block() throws Exception {
		try {
			throw this.done.get();
		} catch (CancellationException | ExecutionException e) {
			return;
		}
	}
	
	/**
	 * The main methods to start the server
	 * @param	args	Optional port number
	 */
	public static void main(String[] args) {
		int port = 8080;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		}
		String databaseDirectory = ".\\db";
		try (
			Server server = new Server(port, Handler.getHandler(databaseDirectory));
		) {
			System.out.println("Server running, press Ctrl+C to quit");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("Main quit server");
				server.close();
			}));
			try {
				server.block();
				System.out.println("Server terminated normally");
			} catch (Exception e) {
				System.out.println("Server terminated unexpected: "+ e.getClass() +", "+ e.getMessage());
			}
		} catch (Throwable e) {
			System.out.println("Server failed to start: "+ e.getClass() +", "+ e.getMessage());
		}
		System.out.println("Server quit application");
	}
}
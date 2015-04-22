package de.tuhh.vs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import de.tuhh.vs.Message;
import de.tuhh.vs.Message.MessageType;

public class Server implements AutoCloseable {
	
	private Thread thread;
	private ServerSocket socket;
	private CompletableFuture<Exception> done;
	private BiConsumer<Message, Consumer<Message>> handler;
	private Vector<Socket> clients;
	
	
	// constructs a message packet and writes it to 'out'
	private void sendResponse(DataOutputStream out, short messageId, Message response) throws IOException {
		int length = response.body != null ? response.body.limit() : 0;
		System.out.println("Server responding "+ messageId +": "+ response.type +":\n"+
				(length != 0 ? "\t("+ length +")"+ App.bytesToHex(response.body.array()) : "<no body>"));
		ByteBuffer buffer = ByteBuffer.allocate(length + Message.LEN_HEADER);
		buffer.put((byte) 0x01);
		buffer.putShort(messageId);
		buffer.put(response.type.get());
		buffer.putLong(length);
		if (response.body != null) {
			buffer.put(response.body.array());
		}
		out.write(buffer.array());
	}
	private void sendResponse(DataOutputStream out, short messageId, MessageType type) throws IOException {
		sendResponse(out, messageId, new Message(type, null));
	}
	
	// handles the communication with a client synchronously
	private void handleClient(Socket client) throws Exception {
		try (
			DataInputStream in = new DataInputStream(client.getInputStream());
			DataOutputStream out = new DataOutputStream(client.getOutputStream());
		) {
			byte version;
			MessageType type;
			ByteBuffer body;
			do {
				System.out.println("Server start read");
				
				version = in.readByte();
				short messageId = in.readShort();

				// check protocol version
				if (version != 0x01) {
					sendResponse(out, messageId, MessageType.InvalidProtocolVersion);
					in.skip(Integer.MAX_VALUE);
					continue;
				}

				// parse packet type
				try {
					type = MessageType.from(in.readByte());
				} catch (IllegalArgumentException e) {
					sendResponse(out, messageId, MessageType.InvalidPacketType);
					in.skip(Integer.MAX_VALUE);
					continue;
				}
				
				// read body of correct length
				try {
					body = ByteBuffer.allocate((int) in.readLong());
					in.read(body.array());
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
							throw new RuntimeException("response haas already been sent");
						}
						this.sendResponse(out, messageId, message);
					} catch (Exception e) {
						System.out.println("Server failed to respond to packet "+ messageId +": "+ e.getMessage());
					}
				});
				
			} while (!Thread.currentThread().isInterrupted());
		} finally {
			client.close();
		}
	}
	
	// constructs a server that asynchronously accepts connections to 'port'
	// and passes the type and body of each incoming message to the handler function,
	// which can then invoke its second argument with its response Message
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
						} catch (EOFException e) {
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							System.out.println("Server client connection closed");
							this.clients.remove(client);
						}
					});
					this.clients.add(client);
					thread.start();
				} while (!Thread.currentThread().isInterrupted());
			} catch (Exception e) {
				this.done.complete(e);
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
	
	// closes the server and interupts all associates threads
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
	
	// closes only the server thread and keeps the client connections alive
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
	
	// blocks until the server thread is done, throws any exceptions the server thread did
	public void block() throws Exception {
		try {
			throw this.done.get();
		} catch (CancellationException | ExecutionException e) {
			return;
		}
	}
}
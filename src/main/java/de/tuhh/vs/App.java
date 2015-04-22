package de.tuhh.vs;

import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import de.tuhh.vs.Message.ProtocolError;

public class App
{
	
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	
	public static void main(String[] args) {
		int port = 8080;
		String databaseDirectory = ".\\target\\db";
		try (
			Server server = new Server(port, Handler.getHandler(databaseDirectory));
		) {
			System.out.println("Test server running, press Ctrl+C to quit");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("Test quit server");
				server.close();
			}));
			App.test(port);
			server.softClose();
			try {
				server.block();
				System.out.println("Test server terminated normally");
			} catch (Exception e) {
				System.out.println("Test server terminated unexpected: "+ e +", "+ e.getMessage());
			}
		} catch (Throwable e) {
			System.out.println("Test ailed to start server: "+ e +", "+ e.getMessage());
		}
		System.out.println("Test quit application");
	}
	
	@SuppressWarnings("unchecked")
	private static void test(int port) throws Exception {
		try (
			Client one = new Client(port);
			Client two = new Client(port);
		) {
			
			one.insert(new Booking("Toast", 0.70));
			two.insert(new Booking("Butter", 0.70));
			
			Booking klopse = new Booking("Klopse", 3.20);
			klopse.setKey((int) one.insert(klopse).get());
			
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
			throw e;
		}
	}
}

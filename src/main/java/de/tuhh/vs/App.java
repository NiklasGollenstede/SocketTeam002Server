package de.tuhh.vs;

public class App
{
	
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	/**
	 * Returns a hexadecimal String representation of the passed byte array
	 * @param	bytes	Byte array from which to read
	 * @return			The hexadecimal String representation of the passed byte array
	 */
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}

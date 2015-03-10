package de.jarnbjo.jsnappy;

/**
 * Exception thrown by the decompressor if it encounters illegal input data. 
 * 
 * @author Tor-Einar Jarnbjo
 * @since 1.0
 */
public class FormatViolationException extends IllegalArgumentException {

	private static final long serialVersionUID = 3430433676859618390L;
	
	private int offset;
	
	public FormatViolationException(String message) {
		super(message);
	}
	
	public FormatViolationException(String message, int offset) {
		this.offset = offset;
	}
	
	public int getOffset() {
		return offset;
	}
	
}

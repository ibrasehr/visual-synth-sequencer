package model;

/**
 * Unchecked exception thrown when a beat-pattern file cannot be saved or loaded.
 *
 * <p>Because the skeleton's {@link SequencerModel#savePattern} and
 * {@link SequencerModel#loadPattern} signatures carry no {@code throws} clause,
 * this exception extends {@link RuntimeException} so it can propagate without
 * changing the agreed-upon API.</p>
 *
 * <p>Every instance carries a <em>user-friendly</em> message suitable for
 * display in a {@link javax.swing.JOptionPane}.</p>
 *
 * @author Member 3 – Data &amp; Persistence Engineer
 */
public class PatternFileException extends RuntimeException {

    /**
     * Constructs a new {@code PatternFileException} with the given
     * user-friendly message.
     *
     * @param message a human-readable description of what went wrong
     */
    public PatternFileException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code PatternFileException} with the given
     * user-friendly message and an underlying cause.
     *
     * @param message a human-readable description of what went wrong
     * @param cause   the underlying exception (e.g. an {@link java.io.IOException})
     */
    public PatternFileException(String message, Throwable cause) {
        super(message, cause);
    }
}

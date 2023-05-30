package nl.bliss.auth;

public interface Identification {

    /**
     * Set the current ID
     * @param id, the ID to set
     */
    void setID(String id);

    /**
     * Retrieve the current ID
     * @return the ID
     */
    String getID();
        
}

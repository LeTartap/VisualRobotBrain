package nl.bliss.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SimpleIdentification implements Identification{

    protected String ID;
    protected ObjectMapper mapper;
    protected static final Logger logger = LoggerFactory.getLogger(SimpleIdentification.class.getName());

    public SimpleIdentification(){
        mapper = new ObjectMapper();
    }
    
    @Override
    public void setID(String id){
        this.ID = id;
    };

    @Override
    public String getID(){
        return this.ID;
    }

    public abstract void start();


}

package nl.bliss.nvlu;

import java.util.List;

public class Intent {

    private INTENT name;
    private List<String> keywords;

    enum INTENT {GREETING, QUESTION, STATEMENT, GOODBYE}

    public Intent(){
        this.name = INTENT.STATEMENT;
    }

    public Intent(INTENT name){
        this.name = name;
    }



}

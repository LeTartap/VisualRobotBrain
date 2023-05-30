package nl.bliss.nvlu;

public class Move {

    private Intent intent;
    private Content content;

    public Move(Intent intent, Content content){
        this.intent = intent;
        this.content = content;
    }

    public Intent getIntent(){
        return this.intent;
    }

    public Content getContent(){
        return this.content;
    }
}

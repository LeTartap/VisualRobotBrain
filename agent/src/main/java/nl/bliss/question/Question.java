package nl.bliss.question;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The current implementation of questions.
 * - They can be open, closed or choice
 * - They can be asked, answered or unanswered
 * - They have a unique ID
 * - They can have conditions, the asking of another question
 * - If it's a choice question, provide the possible choices
 */
public class Question {

    public enum QType {CH,YN,WH};
    public enum Status {ASKED,ANSWERED,UNANSWERED};

    private String question;
    private String id;
    private String answer;
    private QType qtype;
    private Status status;
    private List<Question> conditions;
    private List<String> choices;

    public Question(String id, String answer){
        this.id = id;
        this.answer = answer;
    }

    public Question(String question, String id, List<Question> conditions){
        this.question = question;
        this.id = id;
        this.conditions = conditions;
        this.status = Status.UNANSWERED;
    }


    public Question(String question, String id, QType type, List<Question> conditions, @Nullable List<String> choices){
        this(question,id,conditions);
        this.qtype = type;
        this.status = Status.UNANSWERED;
        this.choices = choices;
    }

    public boolean answered(){
        return this.status.equals(Status.ANSWERED);
    }

    public boolean asked(){
        return this.status.equals(Status.ASKED);
    }

    /**
     * Check if for a list of already asked questions if the current question is valid
     * @param asked, the list of already asked questions
     * @return true if the question can be asked
     */
    public boolean isValid(List<Question> asked){
        for(Question q : this.conditions){
            for(Question qa : asked){
                if(q.id.equals(qa.id)){
                    if(!qa.answered()){
                        return false;
                    }
                    else if(!qa.answer.equals(q.answer)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public String ask(){
        this.status = Status.ASKED;
        return this.question;
    }

    public void setAnswer(String answer){
        switch(this.qtype){
            case CH:
               for(String choice : this.choices){
                    if(answer.toLowerCase().contains(choice.toLowerCase())){
                        this.answer = answer;
                        this.status = Status.ANSWERED;
                    }
                }
                break;
            case YN:
                if(answer.toLowerCase().contains("ja") || answer.toLowerCase().contains("nee")){
                    this.answer = answer;
                    this.status = Status.ANSWERED;
                }
                break;
            case WH:
                if(!answer.equals("")){
                    this.answer = answer;
                    this.status = Status.ANSWERED;
                }
                break;
            default:
                System.err.println("No correct type of the answer");
                break;

        }


    }

   static class QuestionDeserializer extends StdDeserializer<Question> {

        /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

		public QuestionDeserializer(){
            this(null);
        }

        @Override
        public Question deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            String question = node.get("question").asText();
            String id = node.get("id").asText();
            QType type;
            switch(node.get("answertype").asText()){
                case "YN":
                    type = QType.YN;
                    break;
                case "WH":
                    type = QType.WH;
                    break;
                case "CH":
                    type = QType.CH;
                    break;
                default :
                    type = QType.WH;
                    break;
            }
            List<Question> conditions = new ArrayList<>();
            JsonNode array = node.get("conditions");
            if(array.isArray() && array.size() > 0){
                for(final JsonNode object : array){
                    String i = object.get("id").asText();
                    String a = object.get("answer").asText();
                    conditions.add(new Question(i,a));
                }
            }
            List<String> choices = null;
            if(node.has("choices")){
                choices = new ArrayList<>();
                JsonNode choiceNode = node.get("choices");
                for(final JsonNode value : choiceNode){
                    choices.add(value.textValue());
                }
            }
            return new Question(question, id, type, conditions, choices);
        }

        public QuestionDeserializer(Class<Question> q){
            super(q);
        }

    }

}

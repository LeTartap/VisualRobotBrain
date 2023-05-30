package nl.bliss.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class QAsker {

    private static Logger logger = LoggerFactory.getLogger(QAsker.class.getName());
    private List<Question> questions;
    private List<Question> completedQuestions;
    private List<Question> achievedQuestions;
    private static String questionFile;
    private ObjectMapper mapper;
    private SimpleModule module;
    private Question current;
    private boolean finished;

    public QAsker(String questionFile) throws IOException {
        mapper = new ObjectMapper();
        module = new SimpleModule();
        module.addDeserializer(Question.class, new Question.QuestionDeserializer());
        mapper.registerModule(module);

        this.completedQuestions = new ArrayList<>();
        this.achievedQuestions = new ArrayList<>();
        InputStream questions = QAsker.class.getClassLoader().getResourceAsStream(questionFile);
        this.questions = mapper.readValue(questions, mapper.getTypeFactory().constructCollectionType(List.class, Question.class));
        this.finished = false;
    }

    /**
     * Retrieve a question from the question list.
     * @return a valid question to ask.
     */
    public String getValidQuestion(){
        List<Question> validQuestions = new ArrayList<>(this.questions);
        validQuestions.removeAll(this.achievedQuestions);
        validQuestions.removeIf(q -> !q.isValid(this.questions));
        logger.info("Possible questions: {}", validQuestions.size());
        if(validQuestions.size() == 0 && !finished){
            finished = true;
            return "We zijn klaar voor vandaag! Dank u voor het meedoen. Tot ziens!";
        }
        if(finished){
            return "Ik heb geen vragen meer voor u.";
        }
        int randomNum = ThreadLocalRandom.current().nextInt(0, validQuestions.size());
        Question q = validQuestions.get(randomNum);
        current = q;
        return q.ask();

    }

    /**
     * Once a user has answered the question sufficiently, we add the answer to this specific question.
     * @param answer, the answer of the user
     */
    public void addAnswer(String answer){
        current.setAnswer(answer);
        if(current.answered()){
            this.achievedQuestions.add(current);
        }
        else{
            this.completedQuestions.add(current);
        }
    }


    public static void main(String[] args) throws IOException {
        String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: questions";
        if(args.length % 2 != 0){
            logger.error(help);
            System.exit(0);
        }
        for (int i = 0; i < args.length; i = i + 2) {
            if (args[i].equals("-questions")) {
                questionFile = args[i + 1];
            } else {
                logger.error("Unknown commandline argument: \"{}\" {} \".\n {}",args[i],args[i + 1],help);
                System.exit(0);
            }
        }
        QAsker asker = new QAsker(questionFile);
        Scanner scanner = new Scanner(System.in);
        String answer = "";

        while(!asker.achievedQuestions.containsAll(asker.questions) || answer.equals("exit")){
            String question = asker.getValidQuestion();
            System.out.println(question);
            if(scanner.hasNextLine()){
                answer = scanner.nextLine();
                asker.addAnswer(answer);
            }
        }
        scanner.close();

    }
}

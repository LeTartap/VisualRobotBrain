# -*- coding: utf-8 -*-
"""
Created on Sun Aug 11 15:40:29 2019

@author: Jelte
"""
#NLP stuff
from pattern.nl import sentiment as sentiment_nl, parsetree as parsetree_nl, conjugate as conjugate_nl, INFINITIVE as INFINITIVE_nl
from pattern.en import sentiment as sentiment_en, parsetree as parsetree_en, conjugate as conjugate_en, INFINITIVE as INFINITIVE_en
from pattern.es import parsetree as parsetree_es, conjugate as conjugate_es, INFINITIVE as INFINITIVE_es
from pattern.de import parsetree as parsetree_de, conjugate as conjugate_de, INFINITIVE as INFINITIVE_de
from pattern.fr import sentiment as sentiment_fr, parsetree as parsetree_fr, conjugate as conjugate_fr, INFINITIVE as INFINITIVE_fr
from pattern.it import sentiment as sentiment_it, parsetree as parsetree_it, conjugate as conjugate_it, INFINITIVE as INFINITIVE_it 

from spacy.lang.nl.stop_words import STOP_WORDS as STOP_WORDS_NL
from spacy.lang.en.stop_words import STOP_WORDS as STOP_WORDS_EN
from spacy.lang.es.stop_words import STOP_WORDS as STOP_WORDS_ES
from spacy.lang.de.stop_words import STOP_WORDS as STOP_WORDS_DE
from spacy.lang.fr.stop_words import STOP_WORDS as STOP_WORDS_FR
from spacy.lang.it.stop_words import STOP_WORDS as STOP_WORDS_IT

#Utilities
import unicodedata
import base64

#NLU stuff
import fqg
import time
import preprocess as pp
import runSENNA as rs
import contractions
import os 
import random
#https://github.com/saurabhzuve/practNLPTools -> has corrected version of tools.py, though replace it with the accompanied tools.py for token spans.
from practnlptools.tools import Annotator
import spacy
# Build neuralcoref from source, because there is an inconsistency between neuralcoref and spacy.
import neuralcoref

#Time stuff
#pip install git+https://github.com/JMendes1995/py_heideltime.git
from py_heideltime import py_heideltime
from datetime import date

#Network stuff
import hug
import json
from hug_middleware_cors import CORSMiddleware

@hug.post("/base64")
def convertAudioToBase64(body):
    """Converts a wav file into a base64 string, required for Google Speech for example"""
    root = {}    
    # root['base64'] = base64.b64encode(open(body, "rb").read())
    root['base64'] = base64.b64encode(body['data'])
    # root['base64'] = body
    return root

@hug.get("/decode")
def strip_diacritics(text: str):
    """Decodes strings that contain diacritics/accents, such that systems without support for unicode can deal with it"""
    root = {}
    root['original'] = text
    to_decode = text
    try:
        to_decode = unicode(text, 'utf-8')
    except NameError:
        pass
    decoded = str(unicodedata.normalize('NFD', to_decode).encode('ASCII', 'ignore').decode("utf-8"))
    root['decoded'] = decoded
    return root

@hug.get("/stopwords")
def get_current_stopwords():
    return nlp_server.stopwords

def get_stopwords(lang: str, custom_stop_words={}):
    """Retrieves the stopwords for the given language. Languages supported: NL, EN, FR, DE, ES, IT"""
    stopwords = []
    if lang == "en":
        global STOP_WORDS_EN
        STOP_WORDS_EN |= {"think","feel","guess","want","need","get","do","lot","like","must","can","wish"}
        if len(custom_stop_words) > 0:            
            STOP_WORDS_EN |= custom_stop_words
        stopwords = list(STOP_WORDS_EN)
    if lang == "nl":
        global STOP_WORDS_NL
        STOP_WORDS_NL.add("gaan")
        STOP_WORDS_NL |= {"vinden","willen","denken","moeten","houden"}
        if len(custom_stop_words) > 0:            
            STOP_WORDS_NL |= custom_stop_words
        stopwords = list(STOP_WORDS_NL)
    if lang == "de":
        global STOP_WORDS_DE        
        if len(custom_stop_words) > 0:            
            STOP_WORDS_DE |= custom_stop_words
        stopwords = list(STOP_WORDS_DE)
    if lang == "fr":
        global STOP_WORDS_FR        
        if len(custom_stop_words) > 0:            
            STOP_WORDS_FR |= custom_stop_words
        stopwords = list(STOP_WORDS_FR)
    if lang == "it":
        global STOP_WORDS_IT
        if len(custom_stop_words) > 0:            
            STOP_WORDS_IT |= custom_stop_words
        stopwords = list(STOP_WORDS_IT)
    if lang == "es":
        global STOP_WORDS_ES
        if len(custom_stop_words) > 0:            
            STOP_WORDS_ES |= custom_stop_words
        stopwords = list(STOP_WORDS_ES)
    return stopwords

@hug.get("/similarity")
def get_similarity(text_a: str, text_b: str, lang: str):
    """Computes the similarity between two pieces of text for a given language"""
    similarity = {}
    similarity["text_a"] = text_a
    similarity["text_b"] = text_b
    similarity["lang"] = lang
    if nlp_server.lang != lang:    
        set_spacy_language(lang)
    doc_a = nlp_server.nlp(text_a)
    doc_b = nlp_server.nlp(text_b)
    similarity["similarity"] = doc_a.similarity(doc_b)
    return similarity

# The result of this method sends back the last sentence with solved resolutions based on all previous sent texts.
# Sentence should end with punctuation.
@hug.get("/coref")
def get_coref(text: str, lang: str, context=""):    
    """Resolve the references in the sentence (only English)"""
    resolved = {}
    if lang != 'en':
        resolved['error'] = lang + " not supported (yet)"
        return resolved
    if nlp_server.lang != lang:
        set_spacy_language(lang)      
    resolved['context'] = context
    resolved['text'] = text
    resolved['lang'] = lang
    resolved['resolved'] = resolve_text(contractions.fix(text), lang, context)
    return resolved

#Only works for English for now.
def resolve_text(text: str, lang: str, context=""):
    number_of_components = len(list(nlp_server.nlp(text).sents))
    if context != "":
        resolved_text = nlp_server.nlp(context +" "+ text)._.coref_resolved
    else:
        resolved_text = nlp_server.nlp(text)._.coref_resolved    
    norm_text_list = list(nlp_server.nlp(resolved_text).sents)[number_of_components * -1:]
    norm_text = ' '.join(comp.text for comp in norm_text_list)
    return norm_text

@hug.get("/lemmatize")
def lemmatize_text(text: str, lang: str, context=""):
    """Lemmatizes the whole text in the given language. Can use context to solve coreferences first in English"""
    norm_text = contractions.fix(text)
    lemmatized = {}
    lemmatized['text'] = text        
    lemmatized['lang'] = lang
    lemmatized['lemmas'] = []
    if nlp_server.lang != lang:    
        set_spacy_language(lang)        
    if lang == 'en':
        norm_text = resolve_text(text, lang, context)
    doc = nlp_server.nlp(norm_text)
    for lemma in doc:
        if lemma.lemma_ != '-PRON-':
            lemmatized['lemmas'].append(lemma.lemma_)
        else:
            lemmatized['lemmas'].append(lemma.lower_)
    lemmatized['lemmatized'] = " ".join(lemmatized['lemmas'])
    return lemmatized

def merge_np_chunks(token):    
    if token.pos_ == 'PROPN' or token.pos_ == 'NOUN':
        np_chunk = ''
        for elem in token.subtree:
            if elem.dep_ != 'mark':
                if len(np_chunk) == 0:
                    np_chunk = elem.orth_
                else:
                    np_chunk = np_chunk + ' ' + elem.orth_
        return np_chunk
    else:
        raise Exception("Only tokens of type PROPN or NOUN allowed: " + token.pos_)

# The result of this method extracts all the VP chunks
@hug.get("/vps")
def get_verb_chunks(text: str, lang: str, context=""):  
    """Retrieve all verb chunks for this text"""
    verb_chunks = {}
    verb_chunks['chunks'] = []
    verb_chunks['text'] = text
    verb_chunks['context'] = context    
    verb_chunks['lang'] = lang         
    if nlp_server.lang != lang:    
        set_spacy_language(lang)        
    norm_text = ''
    if lang == 'en':
        norm_text = resolve_text(contractions.fix(text), lang, context)        
    else:
        norm_text = text
    # norm_text = lemmatize_text(norm_text,lang,context)['lemmatized']
    doc = nlp_server.nlp(norm_text)        
    for item in doc:
        if item.head.dep_ == 'ROOT' and item in list(item.head.lefts):
            continue        
        elif not item.lemma_ in nlp_server.stopwords and item.pos_ == 'VERB' and len(get_noun_chunks(" ".join(comp.text for comp in item.children), lang)['chunks']) == 0:
            verb_chunks['chunks'].append({"verb":item.lemma_})
        if (item.pos_ == 'NOUN' or item.pos_ == 'PROPN') and item.dep_ != 'ROOT' and item.dep_ !='nsubj':            
            if lang != 'en':
                if not item.head.lemma_ in nlp_server.stopwords and item.head.pos_ == 'VERB':
                    verb_component = ''
                    for child in item.head.children:
                        if child.dep_ == 'compound:prt':
                            verb_component = child.orth_ + item.head.lemma_
                            break
                    if len(verb_component) == 0:
                        verb_component = item.head.lemma_
                    verb_chunks['chunks'].append({"verb":verb_component,"complement": merge_np_chunks(item)})
            else:
                complement = item.orth_                  
                while item.head.pos_ != 'VERB' and item.head.dep_ != 'ROOT':
                    if item.head.dep_ == 'ADP':
                        continue
                    complement = item.head.orth_ + " " + complement
                    item = item.head
                vp = item.head.lemma_
                # if(not vp in nlp_server.stopwords):
                verb_chunks['chunks'].append({"verb":vp,"complement":complement})        
    return verb_chunks

# This method extracts all the NP chunks.
@hug.get("/nps")
def get_noun_chunks(text: str, lang: str, context=""):    
    """Retrieve all noun chunks for this text"""
    noun_chunks = {}
    noun_chunks['chunks'] = []
    noun_chunks['text'] = text
    noun_chunks['context'] = context    
    noun_chunks['lang'] = lang
    norm_text = text
    if nlp_server.lang != lang:    
        set_spacy_language(lang)        
    if lang == 'en':
        norm_text = resolve_text(contractions.fix(text), lang, context)
    # norm_text = lemmatize_text(norm_text,lang,context)['lemmatized']
    doc = nlp_server.nlp(norm_text)
    if has_noun_chunker(doc):
        for chunk in doc.noun_chunks:
            if(not chunk.orth_ in nlp_server.stopwords and not chunk.lemma_ in nlp_server.stopwords):
                if chunk.lemma_ != '-PRON-':
                    noun_chunks['chunks'].append(chunk.lemma_)
    else:        
        for token in doc:
            if not token.lemma_ in nlp_server.stopwords and (token.pos_ == 'PROPN' or token.pos_ == 'NOUN'):                
                if token.lemma_ != '-PRON-':
                    noun_chunks['chunks'].append(token.lemma_)  
    return noun_chunks

def has_noun_chunker(doc):
    if len(list(doc.noun_chunks)) == 0:
        return False
    return True

# This method sets the correct spaCy model and loads the additional pipes: parsing docs as one sentence,
# merging the noun chunks and merge entities.
def set_spacy_language(lang: str):
    print("Resetting pipeline to language: " + lang)
    global nlp_server 
    nlp_server = NLPServer(lang)        

# http://localhost:8190/srl?text=i%20flew%20to%20spain%20for%20my%20two%20week%20holiday%20a%20month%20ago&lang=en
# The result of the srl method, only for English, should look like this for text=i flew to spain for my two week holiday a month ago
# [
#   {
#       'A1': {
#           'text': 'i',
#           'span': '0'
#       },
#       'V': {
#           'text': 'flew',
#           'span': '1'
#       },
#       'A4': {
#           'text' : 'to spain',
#           'span' : '[2,4]'
#       },
#       'AM-TMP':{
#           'text' : 'for my two week holiday a month ago'    
#           'span' : [4,11]
#       } 
#   }
# ]
@hug.get("/srl")
def get_semantic_roles(text: str, lang: str):
    """Retrieve the SRL labels from senna"""
    if nlp_server.lang != lang:    
        set_spacy_language(lang) 
    srl = {}
    if lang != 'en':
        srl['error'] = lang + " not supported yet."
        return srl    
    if lang == "en":
        srl["text"] = text
        srl["normalized"] = resolve_text(contractions.fix(text),lang)
        srl["result"] = nlp_server.annotator.getAnnotations(srl["normalized"])['srl']    
    return srl

# http://localhost:8190/temp_expression?text=i%20flew%20to%20spain%20for%20my%20two%20week%20holiday%20a%20month%20ago&lang=en
# The result of the temp_expression method for text=i flew to spain for my two week holiday a month ago, lang=en, below should look like this:
# [
#   [ 
#     [\"P2W\", \"two week\"], 
#     [\"2020-08\", \"a month ago\"]
#   ], 
#   "i flew to spain for my <d>P2W</d> holiday <d>2020-08</d>\", 
#   "i flew to spain for my <TIMEX3 tid="t3" type="DURATION" value="P2W">two week</TIMEX3> holiday <TIMEX3 tid="t1" type="DATE" value="2020-08">a month ago</TIMEX3>",
#   {
#       "heideltime_processing": 15.444307565689087,
#       "py_heideltime_text_normalization": 0.0
#   }
# ]"
# @hug.get("/temporal")
def get_temporal_expression(text: str, lang: str):
    """Retrieve the temporal expressions in the sentence: list of temponyms, sentence tagged with temponyms, execution time, normalization time.
    Only supports English, Dutch, Spanish, Italian and German"""
    if nlp_server.lang != lang:    
        set_spacy_language(lang) 
    today = date.today()
    doc_time = today.strftime("%Y-%m-%d")
    root = {}
    if lang == "nl":
        temp = py_heideltime(text,language="Dutch",document_type='colloquial', document_creation_time=doc_time)
    elif lang == "es":
        temp = py_heideltime(text,language="Spanish",document_type='colloquial', document_creation_time=doc_time)
    elif lang == "it":
        temp = py_heideltime(text,language="Italian",document_type='colloquial', document_creation_time=doc_time)
    elif lang == "de":
        temp = py_heideltime(text,language="German",document_type='colloquial', document_creation_time=doc_time)
    elif lang == "fr":
        temp = py_heideltime(text,language="French",document_type='colloquial', document_creation_time=doc_time)
    else:    
        temp = py_heideltime(text,language="English",document_type='colloquial', document_creation_time=doc_time)   
    root["timestamps"] = temp[0]
    root["correctedTime"] = temp[1]
    root["TimeML"] = temp[2]
    root["processingTime"] = temp[3]["heideltime_processing"]    
    return root

# http://localhost:8190/sentiment?text=i%20flew%20to%20spain%20for%20my%20great%20two%20week%20holiday%20a%20month%20ago&lang=en
# The result of the sentiment method for text=i flew to spain for my great two week holiday a month ago, lang=en, below should look like this:
# First polarity, then intensity.
# [0.8, 0.75]
@hug.get("/sentiment")
def get_sentiment(text: str, lang: str):
    """Get the sentiment (only supported in EN, NL, IT and FR)"""
    root = {}
    emotion = {}
    if lang == "nl":
        sentiment = sentiment_nl(text)
    elif lang == "fr":
        sentiment = sentiment_fr(text)
    elif lang == "it":
        sentiment = sentiment_it(text)
    # by default english sentiment
    else:
        sentiment = sentiment_en(contractions.fix(text))    
    emotion['polarity'] = sentiment[0]
    emotion['intensity'] = sentiment[1]
    root['sentiment'] = emotion
    return root

# http://localhost:8190/content?text=i%20flew%20to%20spain%20for%20my%20two%20week%20holiday%20a%20month%20ago&lang=en
# The result of the content method for text=i flew to spain for my two week holiday a month ago, lang=en, below should look like this:
# {
#   "VP": ["fly"], 
#   "NP": ["i", "spain", "my two week holiday", "my two week holiday", "my two week holiday", "my two week holiday", "a month", "a month"], 
#   "PP": ["for my two week holiday a month"]
# }
@hug.get("/content")
def get_content(text: str, lang: str):
    """Get the verbs and nouns and their roots + prepositional phrases + their semantic role"""    
    verbs = []
    nouns = []
    prepositions_np = []
    roots = {}
    if lang == "nl":
        tree = parsetree_nl(text, lemmata=True)    
    elif lang == "es":
        tree = parsetree_en(text, lemmata=True)
    elif lang == "de":
        tree = parsetree_de(text, lemmata=True)
    elif lang == "fr":
        tree = parsetree_fr(text, lemmata=True)
    elif lang == "it":
        tree = parsetree_it(text, lemmata=True)
    # by default take the English tree
    else:
        tree = parsetree_en(contractions.fix(text), lemmata=True)
    for sentence in tree.sentences:
        # Extract prepositional phrases
        for pnp in sentence.pnp:
            prepositions_np.append(pnp.string)
        # Extract chunks
        for chunk in sentence.chunks:
            # Extract NPs
            if chunk.type == 'NP':
                for w in chunk.words:
                    if filter_np(chunk):
                        break
                    elif str(chunk.lemmata) not in nouns:
                        nouns.append(str(chunk))
            # Extract verbs
            for w in chunk.words:
                if w.lemma not in nlp_server.stopwords and chunk.type == 'VP':                    
                    if lang == "nl":
                        verbs.append(conjugate_nl(w.string,INFINITIVE_nl))
                    elif lang == "en":
                        verbs.append(conjugate_en(w.string,INFINITIVE_en))
                    else:
                        verbs.append(conjugate_en(w.string,INFINITIVE_en))
    roots['VP'] = verbs
    roots['NP'] = nouns
    roots['PP'] = prepositions_np
    return roots

"""Filters out pronouns and possesive pronouns if there is no NP following"""
def filter_np(chunk):
    last_word_in_chunk = chunk.words[len(chunk.words)-1]          
    for word in chunk:        
        if word.type in ["PRP"]:
            return True        
        if word.type == "PRP$" and word.string == last_word_in_chunk.string:
            return True
    return False

def test():
    test = ['ik vind fietsen op de mijne leuker in mijn woonplaats',
            'bijvoorbeeld op vakantie gaan en nieuwe plaatsen ontdekken vind ik tof.',    
            'ik vind windsurfen wel ontspannend om te doen.',
            'ik heb zitten denken over vakantie naar Spanje.']
    for sentence in test:
        print(get_sentiment(sentence,"nl"))
        print(get_content(sentence,"nl"))


# http://localhost:8190/openquestions?lang=en
# Get all starter questions. Result:
# [
#   "What do you do to get rid of stress?", 
#   "What is something you are obsessed with?", 
#   "What would be your perfect weekend?",
#   "..."
# ]
@hug.get("/openquestions")
def get_starter_questions(lang: str):
    """Retrieve the open questions"""
    root = {}       
    root['openquestions'] = json.dumps(nlp_server.starter_questions)  
    return root

@hug.get("/wait")
def get_response_wait(length: float):
    """Wait in seconds before retrieving (max. 300 seconds)"""
    root = {}
    if length > 300:        
        root['error'] = str(length) + ' exceeds limit of 300 seconds'
        return root
    time.sleep(length)    
    root['waittime'] = length
    return root

@hug.get("/words")
def get_number_of_words(text: str, lang: str):
    """Retrieve the number of words for a given text"""
    root = {}    
    if nlp_server.lang != lang:    
        set_spacy_language(lang)
    doc = nlp_server.nlp(text.strip('"'))
    root["words"] = len(doc)
    root["lang"] = nlp_server.lang
    return root


# http://localhost:8190/followupquestions?lang=en&text=i%20flew%20to%20spain%20for%20my%20two%20week%20holiday%20a%20month%20ago
# Test sentence for follow up questions: i flew to spain for my two week holiday a month ago
# Result:
# [
#   [
#     ["How was it?", "DEF5"], 
#     ["What do you think about spain?", "WHT4"]
#   ]
# ]
@hug.get("/followupquestions")
def get_follow_up_questions(text: str, lang: str):
    """Retrieve the follow-up questions for a sentence"""
    with open(nlp_server.input_path, 'w', encoding='utf-8') as f:
        f.write("%s\n" % text)
    pp.preprocess_senna_input (nlp_server.input_path, nlp_server.senna_input_path) # input is transformed into senna_input (preprocessed & tokenized)
    rs.runSENNA(nlp_server.senna_input_file)
    nlp_server.sentenceList, original = fqg.create_SR(nlp_server.senna_input_path)
    generated_questions = fqg.generate_questions(nlp_server.sentenceList, original)
    root = {}
    root['followupquestions'] = json.dumps(generated_questions)  
    return root
    
# @hug.get("/topicalquestions")    
def get_topic_questions(topic: str, lang: str):
    questions = []
    root = {}
    root['topicalquestions'] = json.dumps(questions)
    return root
            
def test_questions():    
    lang = "en"
    fu_questions = get_follow_up_questions("I like movies and action comics.", lang)    
    print(fu_questions)
    start = True
    answer = ""
    while answer != "exit":
        if start:
            print(random.choice(nlp_server.starter_questions))
            start = False
        else:
            questions = get_follow_up_questions(answer, lang)
            print(questions)
            fu_questions = json.loads(questions['followupquestions'])            
            print(fu_questions)
            print(rs.ranking(fu_questions))  
            start = True
        answer = input("Answer: \n")

def server():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    if __name__ == "__main__":
        import waitress
        app = hug.API(__name__)
        app.http.add_middleware(CORSMiddleware(app))
        waitress.serve(__hug_wsgi__, host='0.0.0.0', port=8190)

class NLPServer():
    def __init__(self, lang: str):
        super().__init__()        
        self.lang = lang
        if self.lang == "nl":
            self.nlp = spacy.load("nl_core_news_lg")        
        else:
            self.nlp = spacy.load("en_core_web_lg")
            coref = neuralcoref.NeuralCoref(self.nlp.vocab)
            self.nlp.add_pipe(coref, name='neuralcoref')
            merge_nps = self.nlp.create_pipe("merge_noun_chunks")
            self.nlp.add_pipe(merge_nps)
            merge_ents = self.nlp.create_pipe("merge_entities")
            self.nlp.add_pipe(merge_ents)

        self.annotator=Annotator()
        self.stopwords=get_stopwords(self.lang)
        work_path = os.getcwd()
        self.senna_path = "/senna/"
        self.input_file = 'input.txt'
        self.input_path = work_path + self.senna_path + self.input_file
        self.senna_input_file = 'input_preprocess.txt' 
        self.senna_input_path = work_path + self.senna_path + self.senna_input_file
        work_path = os.getcwd()
        self.starter_questions = work_path+'/starters.txt'
        with open(self.starter_questions, encoding='utf-8') as f:
            dlines = f.read().splitlines() #reading a file without newlines
        self.starter_questions = dlines

# testQuestions()
nlp_server = NLPServer(lang="en")
server()



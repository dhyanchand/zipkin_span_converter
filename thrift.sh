SPAN_PARSER_HOME="."
plain2base64(){
    echo converting plain text $1 to base64 $2
    java -jar $SPAN_PARSER_HOME/bin/span_parser.jar -r -f$1 -o$2
    echo done 
}

base642plain(){
    echo converting base64 $1 to plain $2
    java -jar $SPAN_PARSER_HOME/bin/span_parser.jar -f$1 -o$2
    echo done
}

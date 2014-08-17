package org.community.tracer;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;

import java.io.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import gnu.getopt.*; 

import org.apache.commons.io.IOUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Span, Endpoint, Annotation, BinaryAnnotation, AnnotationType
import com.twitter.zipkin.gen.*;

/**
 * use a Span object and create various spans
 * start with hardcoded spans 
 * read spans from property
 * read spans from span format
 */
/**
 *
 * validation [53 65 72 76 69 63 65 20 5B 20 55 73 72 47 65 74 50 72 6F 66 69 6C 65 20 5D 20]
Span(trace_id:518417957605460500, name:UsrGetProfile, id:9122103015292044700, parent_id:518417957605460500, annotations:[Annotation(timestamp:1405634437300000, value:cs, host:Endpoint(ipv4:169169524, port:0, service_name://user/login:)), Annotation(timestamp:1405634437307000, value:cr, host:Endpoint(ipv4:169169524, port:0, service_name://user/login:))], binary_annotations:[BinaryAnnotation(key:request, value:53 65 72 76 69 63 65 20 5B 20 55 73 72 47 65 74 50 72 6F 66 69 6C 65 20 5D 20, annotation_type:STRING, host:Endpoint(ipv4:169169524, port:0, service_name://user/login:))])
 */
/** usage: java Base64ToPlain -f infile -o outfile     => converts base64encoded thrift string to a Span object and writes output as plain string
 *         java Base64ToPlain -r -f infile -o outfile  => converts a Span.toString() string to a Span object and writes output as base64 encoded string
 */
class Base64ToPlain {
    private static final java.io.PrintStream out = System.out; 
    private static final byte[] CHUNK_SEPARATOR = {'\n'};
    private static final Logger LOG = LoggerFactory.getLogger(Base64ToPlain.class);

    private static String getBase64EncodedString(final Span span) throws TException {
        return new Base64(Integer.MAX_VALUE, CHUNK_SEPARATOR).encodeToString(spanToBytes(span));
    }

    private static byte[] spanToBytes(final Span thriftSpan) throws TException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final TProtocol proto = new TBinaryProtocol.Factory().getProtocol(new TIOStreamTransport(buf));
        thriftSpan.write(proto);
        return buf.toByteArray();
    }

    private static void writeOutput(String str, String fileName) throws IOException{

        // INFO log
        LOG.info("Writing output to file {} ", fileName);
        FileOutputStream fos = new FileOutputStream(fileName);
        Writer outstream = new OutputStreamWriter(fos, "UTF-8");
        outstream.write(str);
        outstream.close();
        LOG.info("Done writing output to file {}", fileName);
    }

    private static String readInput(String fileName) throws IOException{
        LOG.info("Reading input from file {} ", fileName);
        FileInputStream fis = new FileInputStream(fileName);
        return IOUtils.toString(fis,"UTF-8");
    }

    private static Span getSpanFromBase64EncodedThriftString(String base64Str) throws Exception{
        final Base64 base64 = new Base64();
        final byte[] decodedSpan = base64.decode(base64Str);
        final ByteArrayInputStream buf = new ByteArrayInputStream(decodedSpan);
        final TProtocolFactory factory = new TBinaryProtocol.Factory();
        final TProtocol proto = factory.getProtocol(new TIOStreamTransport(buf));
        final Span span = new Span();
        span.read(proto);
        return span;
    }

    /**
     *-f input-file, -o output-file, -r for unmarshalling a plain text string to a span object
     */
    public static void main(String...args) throws Exception{
        Getopt g = new Getopt("base64toplain", args, "f:o:r::");
        String infile = null, outfile = null;
        boolean reverse = false;
        int c;
        String arg;
        while((c = g.getopt()) != -1){
            switch(c){
                case 'f':
                    infile = g.getOptarg();
                    break;
                case 'o':
                    outfile = g.getOptarg();
                    break;
                case 'r':
                    reverse = true;
                    LOG.info("reverse..");
                    break;
                case '?':
                    usage();
                    System.exit(0);
                default:
                    usage();
            }
        }
        if (infile == null || outfile == null){
            usage();
            System.exit(0);
        }
        LOG.info("Reading File [{}]",infile);
        String fileLines = readInput(infile);
        String[] lines = fileLines.split("\n");
        StringBuilder bldr = new StringBuilder();
        for(String line : lines){
            if (reverse){
                bldr.append(getBase64EncodedThriftStringFromSpan(line));
                //there's no need for a line separator here, it's done by the base64encoder
            }else{
                bldr.append(getSpanFromBase64EncodedThriftString(line));
                bldr.append("\n");
            }
        }

        writeOutput(bldr.toString(),outfile);
        if (reverse){
            out.println("Converted a  PlanTextToBase64 and Saved in ["+outfile+"]");
        } else {
            out.println("Converted a Base64ToPlain Text and Saved in ["+outfile+"]");
        }
    }

    private static String getBase64EncodedThriftStringFromSpan(String line) throws TException{
        //LOG.info("DEBUG: base64EncodedThriftString - line read ###\n"+line);
        return getBase64EncodedString(SpanStruct.getSpan(line));
    }

    private static String usageText ="usage: java -jar <path to jar>  -f infile -o outfile     => converts base64encoded thrift string to a Span object and writes output as plain string\n"
                                      +"java -jar <path to jar> -r -f infile -o outfile  => converts a Span.toString() string to a Span object and writes output as base64 encoded string\n";

    private static void usage(){
        LOG.info("\n--------------Both -f and -o are required------------------");
        LOG.info(usageText);
    }

    //should this be an enum?
    private static class SpanStruct{
        //mandatory in a span
        private static final String SPAN_REGEX="Span\\s*\\(\\s*trace_id\\:(.*),\\s*name\\s*\\:(.*),\\s*id\\s*\\:(.*),\\s*annotations\\s*:(.*),\\s*binary_annotations\\s*:(.*)";
        private static final Pattern SPAN_PATTERN = Pattern.compile(SPAN_REGEX); 
        //annotations
        private static final String ANNOTATION_REGEX="timestamp\\s*\\:(.*),\\s*value\\s*\\:(.*)";
        private static final Pattern ANNOTATION_PATTERN = Pattern.compile(ANNOTATION_REGEX); 
        //binary annotations
        private static final String BIN_ANNOTATION_REGEX="key\\s*\\:(.*),\\s*value\\s*\\:(.*),\\s*annotation_type\\s*\\:(.*)";
        private static final Pattern BIN_ANNOTATION_PATTERN = Pattern.compile(BIN_ANNOTATION_REGEX); 
        //host/endpoint
        private static final String EP_REGEX="ipv4\\s*\\:(.*),\\s*port\\s*\\:(.*),\\s*service_name\\s*\\:(.*)";
        private static final Pattern EP_PATTERN = Pattern.compile(EP_REGEX); 

        private static final Map<String, AnnotationType> ANNOT_TYPE_MAP = new HashMap<String, AnnotationType>();
        static{
            ANNOT_TYPE_MAP.put("BOOL", AnnotationType.BOOL);
            ANNOT_TYPE_MAP.put("BYTES", AnnotationType.BYTES);
            ANNOT_TYPE_MAP.put("I16", AnnotationType.I16);
            ANNOT_TYPE_MAP.put("I32", AnnotationType.I32);
            ANNOT_TYPE_MAP.put("I64", AnnotationType.I64);
            ANNOT_TYPE_MAP.put("DOUBLE", AnnotationType.DOUBLE);
            ANNOT_TYPE_MAP.put("STRING", AnnotationType.STRING);
        }

        private static String traceId = null;
        private static String name = null;
        private static String id = null;
        private static String parentId = null; //optional
        private static String annotations = null; //might be null, @see com.twitter.span.gen.Span.toString() method
        private static String binaryAnnotations = null; //might be null, @see com.twitter.span.gen.Span.toString() method
        private static List<Annotation> thriftAnnotationList = null;
        private static List<BinaryAnnotation> thriftBinaryAnnotationList = null;

        public static Span getSpan(String spanStr){
            Matcher spanMatcher = SPAN_PATTERN.matcher(spanStr);
            if (spanMatcher.find()){
                int spanGroups = spanMatcher.groupCount()+1;
                for(int spanGroup = 0; spanGroup<spanGroups; spanGroup++){
                    //LOG.info("Group-"+spanGroup+"#### "+ spanMatcher.group(spanGroup));
                    if (spanGroup == 1){ // traceId
                        traceId = spanMatcher.group(spanGroup);
                    } else if (spanGroup == 2){ // name
                        name = spanMatcher.group(spanGroup);
                    } else if (spanGroup == 3){ // id
                        // this may contain id and parent_id
                        id = spanMatcher.group(spanGroup);
                        String[] ids = id.split(",");
                        if (id.indexOf("parent_id") > 0){
                            id = ids[0];
                            parentId = ids[1].split(":")[1];
                        } 
                        else{
                            parentId = null;
                        }
                    } else if (spanGroup == 4){ //annotations
                        // this contains all annotations
                        annotations = spanMatcher.group(spanGroup);
                        thriftAnnotationList = getAnnotations(annotations);
                    } else if (spanGroup == 5){ //binary annotations
                        // this contains all binary annotations
                        binaryAnnotations = spanMatcher.group(spanGroup);
                        thriftBinaryAnnotationList = getBinaryAnnotations(binaryAnnotations);
                    } 
                }
            }
            //LOG.info("traceId ##"+traceId+" id ##"+ id);
            Span thriftSpan = new Span(Long.valueOf(traceId), name, Long.valueOf(id), thriftAnnotationList, thriftBinaryAnnotationList);
            if (parentId != null){
                thriftSpan.setParent_id(Long.valueOf(parentId));
            }
            int debugPos = spanStr.indexOf("debug");
            if (debugPos>0){
                String thriftDebug = spanStr.substring(debugPos, spanStr.lastIndexOf(")")).split(":")[1];
                boolean debugBool = Boolean.valueOf(thriftDebug);
                //LOG.info("debugBool is ####"+debugBool);
                thriftSpan.setDebug(debugBool);
            }
            //do this explicitly so we can do re-runs..
            nullAllFields();
            return thriftSpan;
        }

        private static final void nullAllFields(){
            traceId = null; name=null; id=null; parentId=null; annotations=null; binaryAnnotations=null;
            thriftAnnotationList = null; thriftBinaryAnnotationList = null;
        }

        //for annotations and binaryannotations find all occurrences but first check if it's null
        private static List<BinaryAnnotation> getBinaryAnnotations(String binaryAnnotations){
            String[] temp = binaryAnnotations.split(":");
            if (temp.length == 1 ) {
                //just one value of annotation and it's null
                //LOG.info("DEBUG: binaryAnnotation value is null###"+temp[0]);
                return null;
            }
            String binaryAnnotationValues = binaryAnnotations.split("\\[")[1];
            List<BinaryAnnotation> thriftBinaryAnnotations = new ArrayList<BinaryAnnotation>();
            if (binaryAnnotationValues != null){
                //LOG.info("DEBUG: binaryAnnotation values ###"+binaryAnnotationValues);
                String[] binAnnotList = binaryAnnotationValues.split("BinaryAnnotation");
                for (int i=1; i<binAnnotList.length; i++){
                    BinaryAnnotation thriftBinaryAnnotation = getOneBinaryAnnotation(binAnnotList[i]);
                    if (thriftBinaryAnnotation != null) {
                        thriftBinaryAnnotations.add(thriftBinaryAnnotation);
                    }
                }
            }
            return thriftBinaryAnnotations;
        }

        /**String annotation value expected
         * binary_annotations:null)
         * binary_annotations:[BinaryAnnotation(key:request, value:53 65 72 76 69 63 65 20 5B 20 55 73 72 47 65 74 50 72 6F 66 45 78 74 20 5D 20, annotation_type:STRING, host:Endpoint(ipv4:169169524, port:0, service_name://user/login:zipkin-test))])"
         */
        private static BinaryAnnotation getOneBinaryAnnotation(String binaryAnnotation){
            String key=null;
            String value=null;
            String annotationType=null;
            Endpoint ep = null;
            String debug=null;
            Matcher genAnnotMatcher = BIN_ANNOTATION_PATTERN.matcher(binaryAnnotation); 
            if(genAnnotMatcher.find()){
                int genGroups = genAnnotMatcher.groupCount()+1;
                for(int genGroup=0; genGroup<genGroups; genGroup++){
                    if (genGroup==1){
                        key = genAnnotMatcher.group(1);
                    } else if (genGroup==2){
                        value = genAnnotMatcher.group(2);
                        value = convertHexToString(value); 
                    } else if (genGroup==3){
                        annotationType = genAnnotMatcher.group(3);
                        //LOG.info("DEBUG: annotationType string ##"+ annotationType);
                        int endPointPos = annotationType.indexOf("Endpoint");
                        //the capture group gets debug too, so need to avoid
                        //so we take care of it
                        int debugPos = annotationType.indexOf("debug");
                        if (endPointPos > 0 || debugPos > 0){ 
                            //this may contain endpoint
                            if (endPointPos > 0 ){
                                //LOG.info("DEBUG: FOUND HOST/ENDPOINT");
                                ep = getEndpoint(annotationType);
                            }
                            //finally reset value
                            annotationType = annotationType.substring(0,annotationType.indexOf(",")); 
                        } else{
                            annotationType = annotationType.substring(0,annotationType.indexOf(")")); 
                            //LOG.info("DEBUG: NO HOST/ENDPOINT");
                        }
                    }
                }
            }
            //LOG.info("DEBUG: key ###"+key+" value ####"+ value+" annotation_type ###"+annotationType);
            //this is the minimum required
            BinaryAnnotation thriftBinaryAnnotation = null;
            try{
                thriftBinaryAnnotation =  new BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes("UTF-8")),ANNOT_TYPE_MAP.get(annotationType));
            } catch(java.io.UnsupportedEncodingException unsupportedencoding){
                //so what should i do??
            }
            if (ep!=null){
                thriftBinaryAnnotation.setHost(ep);
            }
            return thriftBinaryAnnotation;
        }



        //[Annotation(timestamp:1405553299853000, value:cs, host:Endpoint(ipv4:169169524, port:0, service_name://user/login:zipkin-test)), Annotation(timestamp:1405553299867000, value:cr, host:Endpoint(ipv4:     169169524, port:0, service_name://user/login:zipkin-test))]
        //for annotations and binaryannotations find all occurrences but first check if it's null
        private static List<Annotation> getAnnotations(String annotations){
            String[] temp = annotations.split(":");
            if (temp.length == 1 ) {
                //just one value of annotation and it's null
                //LOG.info("DEBUG: annotation value is null###"+temp[0]);
                return null;
            }
            List<Annotation> thriftAnnotations = new ArrayList<Annotation>();
            String annotationValues = annotations.split("\\[")[1];
            if (annotationValues != null){
                //LOG.info("DEBUG: annotation values ###"+annotationValues);
                String[] annotList = annotationValues.split("Annotation");
                for (int i=1; i<annotList.length; i++){
                    Annotation thriftAnnotation = getOneAnnotation(annotList[i]);
                    if (thriftAnnotation != null) {
                        thriftAnnotations.add(thriftAnnotation);
                    }
                }
            }
            return thriftAnnotations;
        }

        /**String annotation value expected
         * 1. timestamp:1405553299853000, value:cs, host:Endpoint(ipv4:169169524, port:0, service_name://user/login:zipkin-test)
         * 2. timestamp:1405553299853000, value:cs)
         */
        private static Annotation getOneAnnotation(String annotation){
            String timestamp=null;
            String value=null;
            Endpoint ep = null;
            Matcher genAnnotMatcher = ANNOTATION_PATTERN.matcher(annotation); 
            if(genAnnotMatcher.find()){
                int genGroups = genAnnotMatcher.groupCount()+1;
                for(int genGroup=0; genGroup<genGroups; genGroup++){
                    if (genGroup==1){
                        timestamp = genAnnotMatcher.group(1);
                    } else if (genGroup==2){
                        value = genAnnotMatcher.group(2);
                        //this may contain endpoint
                        if (value.indexOf("Endpoint") > 0) {
                            //LOG.info("DEBUG: FOUND HOST/ENDPOINT");
                            ep = getEndpoint(value);
                            //reset value
                            value = value.substring(0,value.indexOf(",")); 
                        } else{
                            value = value.substring(0,value.indexOf(")")); 
                            //LOG.info("DEBUG: NO HOST/ENDPOINT");
                        }
                    }
                }
            }
            //LOG.info("timestamp ###"+timestamp+" value ####"+ value);
            //this is the minimum required
            Annotation thriftAnnotation = new Annotation(Long.valueOf(timestamp), value);
            if (ep!=null){
                thriftAnnotation.setHost(ep);
            }
            return thriftAnnotation;
        }

        private static Endpoint getEndpoint(String endpoint){
            String ipv4=null; 
            String port=null;
            String serviceName=null;
            Matcher epMatcher = EP_PATTERN.matcher(endpoint);
            if(epMatcher.find()){
                int epGroups = epMatcher.groupCount()+1;
                for(int epGroup = 0; epGroup<epGroups; epGroup++){
                    if (epGroup == 1){
                        ipv4 = epMatcher.group(1);
                    } else if (epGroup == 2){
                        port = epMatcher.group(2);
                    }else if (epGroup == 3){
                        serviceName = epMatcher.group(3);
                        serviceName = serviceName.substring(0,serviceName.indexOf(")"));
                    }
                }
            }
            //LOG.info("DEBUG: endpoint ipv4###"+ipv4+" port###"+port+" serviceName###"+serviceName);
            if (ipv4 != null && port != null && serviceName != null){
             return new Endpoint(Integer.valueOf(ipv4), Short.valueOf(port), serviceName);
            }
            return null;
        }

        /** 53 65 72 76 69 63 65 20 5B 20 55 73 72 47 65 74 50 72 6F 66 45 78 74 20 5D 20 **/
        private static String convertHexToString(String hex){
            StringBuilder sb = new StringBuilder();
            String[] hexes = hex.split(" ");
            for (String hexStr : hexes){
                sb.append((char)Integer.parseInt(hexStr,16));
            }
            return sb.toString();
        }
    }
}

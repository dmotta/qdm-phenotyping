/*
 * Copyright: (c) 2004-2012 Mayo Foundation for Medical Education and 
 * Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 * triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 * Except as contained in the copyright notice above, or as used to identify 
 * MFMER as the author of this software, the trade names, trademarks, service
 * marks, or product names of the copyright holder shall not be used in
 * advertising, promotion or otherwise in connection with this software without
 * prior written authorization of the copyright holder.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.mayo.qdm.webapp.rest.controller;

import edu.mayo.qdm.webapp.rest.model.Execution;
import edu.mayo.qdm.webapp.rest.model.Executions;
import edu.mayo.qdm.webapp.rest.store.*;
import edu.mayo.qdm.webapp.rest.store.ExecutionInfo.Status;
import edu.mayo.qdm.webapp.rest.xml.XmlProcessor;
import edu.mayo.qdm.webapp.translator.ExecutionResult;
import edu.mayo.qdm.webapp.translator.Launcher;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Class TranslatorController.
 *
 * @author <a href="mailto:kevin.peterson@mayo.edu">Kevin Peterson</a>
 */
@Controller
public class TranslatorController {
	
	private static final MediaType DEFAULT_ACCEPT_HEADER = MediaType.APPLICATION_XML;

    private Logger log = Logger.getLogger(this.getClass());
	
	@Resource
	private DateValidator dateValidator;

	@Resource
	private IdGenerator idGenerator;
	
	@Resource
	private FileSystemResolver fileSystemResolver;
	
	@Resource
	private Launcher launcher;
	
	@Resource 
	private XmlProcessor xmlProcessor;
	
	private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @RequestMapping(value = "/executor", method = RequestMethod.GET)
    public ModelAndView getExecutorHome(){
        return new ModelAndView("executor");
    }

    @RequestMapping(value = "/executor/api", method = RequestMethod.GET)
    public ModelAndView getExecutorApi(){
        return new ModelAndView("executor/api");
    }

	/**
	 * Gets the exceuctions.
	 *
	 * @param request the request
	 * @return the exceuctions
	 * @throws Exception the exception
	 */
	@RequestMapping(value = "executor/executions", method=RequestMethod.GET)
	public Object getExceuctions(HttpServletRequest request) throws Exception {
		
		String requestUrl = request.getRequestURL().toString();
		requestUrl = StringUtils.replace(requestUrl, "/executions", "/execution/{id}/{resource}");
		
		UriTemplate template = new UriTemplate(requestUrl);
		
		Set<ExecutionInfo> dataFiles = this.fileSystemResolver.getExecutionInfo();

		Executions executions = new Executions(dataFiles, template);
		String xml = xmlProcessor.executionsToXml(executions);

	    return this.buildResponse(request, executions, xml);
	}
	
	/**
	 * Gets the exceuction.
	 *
	 * @param request the request
	 * @param executionId the execution id
	 * @return the exceuction
	 * @throws Exception the exception
	 */
	@RequestMapping(value = "executor/execution/{executionId}", method=RequestMethod.GET)
	public Object getExceuction(
			HttpServletRequest request,
			@PathVariable String executionId) throws Exception {
		
		String requestUrl = request.getRequestURL().toString();
		requestUrl = StringUtils.substringBeforeLast(requestUrl, "/execution/") + "/execution/{id}/{resource}";
		
		UriTemplate template = new UriTemplate(requestUrl);
		
		Execution execution = 
				new Execution(this.fileSystemResolver.getExecutionInfo(executionId), template);

		String xml = xmlProcessor.executionToXml(execution);

	    return this.buildResponse(request, "executor/execution", execution, xml);
	}
	
	@RequestMapping(value = "executor/execution/{executionId}", method=RequestMethod.DELETE)
	public ModelAndView deleteExceuction(
			HttpServletRequest request,
			HttpServletResponse response,
			@PathVariable String executionId) throws Exception {
		this.fileSystemResolver.remove(executionId);

	    if(this.isHtmlRequest(request)){
			return new ModelAndView("redirect:/executor/executions");
		} else {
			response.setStatus(HttpStatus.OK.value());
			
			return null;
		}
	}

	/**
	 * Creates the exceuction.
	 *
	 * @param request the request
	 * @param startDate the start date
	 * @param endDate the end date
	 * @return the object
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@RequestMapping(value = "/executor/executions", method=RequestMethod.POST)
	public synchronized Object createExceuction(
			HttpServletRequest request,
			final @RequestParam(required=true) String startDate,
			final @RequestParam(required=true) String endDate,
            final @RequestParam(required=false) String valueSetDefinitions) throws Exception {
		//For now, don't validate the date. This requirement may
		//come back at some point.
		//this.dateValidator.validateDate(startDate, DateType.START);
		//this.dateValidator.validateDate(endDate, DateType.END);
		
		if(! (request instanceof MultipartHttpServletRequest)){
			throw new IllegalStateException("ServletRequest expected to be of type MultipartHttpServletRequest");
		}

        final Map<String,String> valueSetDefinitionsMap = valueSetDefinitions != null
          ? new ObjectMapper().readValue(valueSetDefinitions, HashMap.class) : Collections.EMPTY_MAP;

        final MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
		final MultipartFile multipartFile = multipartRequest.getFile("file");

        final String id = this.idGenerator.getId();

        final FileSystemResult result = this.fileSystemResolver.getNewFiles(id);
        FileUtils.copyInputStreamToFile(multipartFile.getInputStream(), result.getInputQdmXml());

        String xmlFileName = multipartFile.getOriginalFilename();

        final ExecutionInfo info = new ExecutionInfo();
        info.setId(id);
        info.setStatus(Status.PROCESSING);
        info.setStart(new Date());
        info.setParameters(new Parameters(startDate, endDate, xmlFileName));

        this.fileSystemResolver.setExecutionInfo(id, info);

        this.executorService.submit(new Runnable(){

            @Override
            public void run() {
                ExecutionResult translatorResult = null;
                try {

                    translatorResult = launcher.launchTranslator(
                            IOUtils.toString(multipartFile.getInputStream()),
                            dateValidator.parse(startDate),
                            dateValidator.parse(endDate),
                            valueSetDefinitionsMap);

                    info.setStatus(Status.COMPLETE);
                    info.setFinish(new Date());
                    fileSystemResolver.setExecutionInfo(id, info);

                    FileUtils.writeStringToFile(result.getOuptutResultXml(), translatorResult.getXml());
                } catch (Exception e) {
                    info.setStatus(Status.FAILED);
                    info.setFinish(new Date());

                    info.setError(ExceptionUtils.getFullStackTrace(e));
                    fileSystemResolver.setExecutionInfo(id, info);

                    log.warn(e);
                    throw new RuntimeException(e);
                }
            }

        });

        if(this.isHtmlRequest(multipartRequest)){
            return new ModelAndView("redirect:/executor/executions");
        } else {
            String locationUrl = "executor/execution/" + id;

            final HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(locationUrl));

            return new ResponseEntity(headers, HttpStatus.CREATED);
        }

	}

	/**
	 * Gets the xml.
	 *
	 * @param executionId the execution id
	 * @return the xml
	 * @throws Exception the exception
	 */
	@RequestMapping(value = "executor/execution/{executionId}/xml", method=RequestMethod.GET)
	public ResponseEntity<String> getOutputXml(@PathVariable String executionId) throws Exception {
		File xml = fileSystemResolver.getFiles(executionId).getOuptutResultXml();

		String xmlString = FileUtils.readFileToString(xml, "UTF-8");
		
		final HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_XML);

	    return new ResponseEntity<String>(xmlString, headers, HttpStatus.OK);
	}

    @RequestMapping(value = "executor/execution/{executionId}/input", method=RequestMethod.GET)
    public ResponseEntity<String> getInputputXml(@PathVariable String executionId) throws Exception {
        File xml = fileSystemResolver.getFiles(executionId).getInputQdmXml();

        String xmlString = FileUtils.readFileToString(xml, "UTF-8");

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        return new ResponseEntity<String>(xmlString, headers, HttpStatus.OK);
    }

    @RequestMapping(value = "executor/execution/{executionId}/xslt/eMeasure.xsl", method=RequestMethod.GET)
    public ResponseEntity<String> getMeasureXslt(@PathVariable String executionId) throws Exception {
        File xml = fileSystemResolver.getFiles(executionId).getInputQdmXml();

        String xmlString = IOUtils.toString(new ClassPathResource("/eMeasure.xsl").getInputStream(), "UTF-8");

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        return new ResponseEntity<String>(xmlString, headers, HttpStatus.OK);
    }

	/**
	 * Checks if is html request.
	 *
	 * @param request the request
	 * @return true, if is html request
	 */
	protected boolean isHtmlRequest(HttpServletRequest request){
		String acceptHeader = request.getHeader("Accept");
		
		List<MediaType> types;
		
		if(StringUtils.isBlank(acceptHeader)){
			types = Arrays.asList(DEFAULT_ACCEPT_HEADER);
		} else {
			types = MediaType.parseMediaTypes(acceptHeader);
		}

		MediaType.sortByQualityValue(types);
		
		MediaType type = types.get(0);
		
		return type.isCompatibleWith(MediaType.TEXT_HTML);
	}
	
	protected Object buildResponse(HttpServletRequest request, Object bean, String xml){
		return this.buildResponse(request, null, bean, xml);
	}
	
	/**
	 * Builds the response.
	 *
	 * @param request the request
	 * @param bean the bean
	 * @param xml the xml
	 * @return the object
	 */
	protected Object buildResponse(HttpServletRequest request, String view, Object bean, String xml){
		
		if(this.isHtmlRequest(request)){
			ModelAndView mav;
			if(StringUtils.isNotBlank(view)){
				mav = new ModelAndView(view);
			} else {
				mav = new ModelAndView();
			}
			mav.addObject(bean.getClass().getSimpleName(), bean);
			return mav;
		} else {
			final HttpHeaders headers = new HttpHeaders();
		    headers.setContentType(MediaType.APPLICATION_XML);
		    
			return new ResponseEntity<Object>(xml, headers, HttpStatus.OK);
		}
	}
	
	@ExceptionHandler(ExecutionNotFoundException.class)
	public void handleException(
			HttpServletResponse response, 
			HttpServletRequest request, 
			RuntimeException ex) {
	
		int status = HttpServletResponse.SC_NOT_FOUND;
		
		response.setStatus(status);
	}
	
	@ExceptionHandler(UserInputException.class)
	@ResponseBody
	public String handleNullPointerException(
			HttpServletResponse response,
			UserInputException ex) {

		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		
		return ex.getMessage();
	}

	public FileSystemResolver getFileSystemResolver() {
		return fileSystemResolver;
	}

	public void setFileSystemResolver(FileSystemResolver fileSystemResolver) {
		this.fileSystemResolver = fileSystemResolver;
	}

	public XmlProcessor getXmlProcessor() {
		return xmlProcessor;
	}

	public void setXmlProcessor(XmlProcessor xmlProcessor) {
		this.xmlProcessor = xmlProcessor;
	}
	
}

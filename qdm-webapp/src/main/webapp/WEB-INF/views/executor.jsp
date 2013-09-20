<!DOCTYPE HTML>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
		<c:if test="${Executions != null}">
			<c:set var="pending" value="false" scope="page" />
		
	   		<c:forEach var="loop" items="${Executions.executions}">
	   			<c:if test="${ loop.status eq 'PROCESSING' }">
	   				<c:set var="pending" value="true" scope="page"/>
	   			</c:if>
	   		</c:forEach>
	   		
	   		<c:if test="${ pending }">
	   			<%
	   			out.print("<META HTTP-EQUIV=\"Refresh\" CONTENT=\"6\" ");
	   			%>
	   		</c:if>
	   	</c:if>
		
		<title>Algorithms</title>

        <script type="text/javascript" src="resources/include/jquery-ui-1.8.19.custom/js/jquery-1.7.2.min.js"></script>
        <script type="text/javascript" src="resources/include/bootstrap/js/bootstrap.min.js"></script>
        <script type="text/javascript" src="resources/include/bootstrap-fileupload/bootstrap-fileupload.min.js"></script>
        <script type="text/javascript" src="resources/include/datepicker/js/bootstrap-datepicker.js"></script>
        <script type="text/javascript" src="resources/include/jquery.validate/jquery.validate.min.js"></script>
        <script type="text/javascript" src="resources/include/jquery.tablesorter/jquery.tablesorter.js"></script>

        <link rel="stylesheet" href="resources/include/bootstrap/css/bootstrap.min.css">
        <link rel="stylesheet" href="resources/include/bootstrap-fileupload/bootstrap-fileupload.min.css">
        <link rel="stylesheet" href="resources/include/datepicker/css/datepicker.css" />
        <link rel="stylesheet" href="resources/include/fontawesome/css/font-awesome.min.css" />

        <style type="text/css">
            body {
                padding-top: 65px;
            }
            footer {
                height: 60px;
            }

            footer {
                background-color: #f5f5f5;
            }
            .credit {
                margin: 20px 0;
            }
		</style>
		<script>
		
		  $(document).ready(function() {
			    
			    $( ".datePicker" ).datepicker({
                    autoclose:true,
                    format:"dd-M-yyyy"
                });
			    
			    $.extend(jQuery.validator.messages, {
			        required: "<br/>This field is required."
			    });
			    
			    $("#executionForm").validate();
			 
			    $("#executionsTable").tablesorter( {sortList: [ [0,0] ] } ); 
		  });
		</script>
	</head>
  <body>
  <div class="navbar navbar-fixed-top">

      <div class="navbar-inner">

          <div class="container">
              <a href="https://github.com/SHARP-HTP/qdm2json"><img style="position: absolute; top: 0; right: 0; border: 0;" src="https://s3.amazonaws.com/github/ribbons/forkme_right_red_aa0000.png" alt="Fork me on GitHub"></a>

              <a class="brand" href="#">
                  QDM Phenotyping Executor
              </a>

              <ul class="nav">
                  <li class="divider-vertical"></li>
                  <li><a href="./">Home</a></li>
                  <li class="divider-vertical"></li>
                  <li><a href="executor/api">API</a></li>
                  <li class="divider-vertical"></li>
              </ul>

          </div>
      </div>
  </div>

    <div class="container">

        <div class="hero-unit">
            <h1>Execution/Phenotyping</h1>
            <div>
                <a href="qdm2drools">PhenotypePortal</a>
            </div>
            <div>
                <a href="qdm2drools">Cypress Validation Report</a>
            </div>
        </div>
        <div class="row">
            <div class="span6">
                <h3>Execute Algorithm</h3>
            <form action="executor/executions" id="executionForm" method="post" class="form-horizontal" enctype="multipart/form-data">

                <div class="control-group">
                    <label class="control-label" for="file">XML:</label>
                    <div class="controls">
                        <input class="required" type="file" id="file" name="file" />
                    </div>
                </div>
                <div class="control-group">
                    <label class="control-label" for="startDate">Start Date:</label>
                    <div class="controls">
                        <input class="required datePicker" type="text" id="startDate" name="startDate"/>
                    </div>
                </div>
                <div class="control-group">
                    <label class="control-label" for="endDate">End Date:</label>
                    <div class="controls">
                        <input class="required datePicker" type="text" id="endDate" name="endDate"/>
                    </div>
                </div>
                <div class="control-group">
                    <div class="controls">
                        <button type="submit" class="btn" name="submit">Execute Algorithm</button>
                    </div>
                </div>
            </form>
            </div>
            <div class="span6">
                <h3>Cypress Validation</h3>
                    <blockquote>
                        <p>The objective of Cypress is to enable repeatable and rigorous testing of an EHR's ability to accurately calculate Meaningful Use Stage 2 Eligible Provider (EP) and Eligible Hospital (EH) Clinical Quality Measures. Cypress has been recognized by ONC as the official CQM testing tool for operational use in Meaningful Use Clinical Quality Measure certification.</p>
                        <small>For more information see <a href="http://projectcypress.org/">Project Cypress</a></small>
                    </blockquote>
                    <a class="btn btn-large btn-success" href="executor/cypress/report"><i class="icon-ok icon-large"></i> Run Cypress Validation</a>
            </div>
            </div>

        </div>

  <footer class="navbar navbar-fixed-bottom">
      <div class="container">
          <p class="muted credit">
              Powered by the <a href="https://github.com/projectcypress/health-data-standards">hqmf-parser</a>,
              <a href="https://ushik.ahrq.gov/">USHIK</a>,
              and the <a href="https://vsac.nlm.nih.gov/">NLM VSAC</a>,
              For more information see the
              <a href="http://phenotypeportal.org/">Phenotype Portal</a>.
          </p>
      </div>
  </footer>

  </body>
</html>
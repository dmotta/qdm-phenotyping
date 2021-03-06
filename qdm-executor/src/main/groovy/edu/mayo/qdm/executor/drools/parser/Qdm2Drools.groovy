package edu.mayo.qdm.executor.drools.parser
import edu.mayo.qdm.executor.MeasurementPeriod
import edu.mayo.qdm.executor.ResultCallback
import edu.mayo.qdm.executor.drools.DroolsUtil
import edu.mayo.qdm.executor.drools.PreconditionResult
import edu.mayo.qdm.executor.drools.SpecificOccurrence
import edu.mayo.qdm.executor.drools.parser.criteria.CriteriaFactory
import edu.mayo.qdm.executor.drools.parser.criteria.Interval
import edu.mayo.qdm.executor.drools.parser.criteria.MeasurementValue
import edu.mayo.qdm.patient.*
import groovy.util.logging.Log4j
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.ByteArrayBody
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
/*
 * The main JSON -> Drools converter.
 */
@Log4j
@Component
class Qdm2Drools {

    @org.springframework.beans.factory.annotation.Value('${drools.qdm2jsonService:http://qdm2json.phenotypeportal.org}')
    String qdm2jsonServiceUrl;

    @Autowired
    CriteriaFactory criteriaFactory;

    Qdm2Drools() {
        super()
    }

    Qdm2Drools(String qdm2jsonServiceUrl) {
        super()
        this.qdm2jsonServiceUrl = qdm2jsonServiceUrl
    }

    /**
     * Generate a JSON representation of a QDM/HQMF XML file.
     *
     * @param qdmXml the QDM/HQMF XML file as a String.
     * @return the JSON representation
     */
    protected def getJsonFromQdmFile(qdmXml) {

        def http = new HTTPBuilder(this.qdm2jsonServiceUrl + "/qdm2json")

        def resp = http.request(Method.POST) { req ->
            requestContentType = 'multipart/form-data'

            def mpEntity = new MultipartEntity()
            mpEntity.addPart("file", new ByteArrayBody(qdmXml.getBytes("UTF-8"), "qdm.xml"))

            req.entity = mpEntity
        }

        resp
    }


    def String qdm2drools(String qdmXml) {
        def json = getJsonFromQdmFile(qdmXml)

        def sb = new StringBuilder()

        sb.append(printRuleHeader(json))

        json.population_criteria.each {
            printPopulationCriteria(it, sb)
        }
        json.data_criteria.each {
            sb.append(printDataCriteria(it, json))
        }

        def rule = sb.toString()

        log.debug(rule)

        rule
    }

    /**
     * Prints header/metadata info for the Drools rule.
     */
    private def printRuleHeader(qdm) {
        """
        import ${Set.name};
        import ${Date.name};
        import ${Calendar.name};
        import ${Map.name};
        import ${PreconditionResult.name};
        import ${ResultCallback.name};
        import ${Patient.name};
        import ${Concept.name};
        import ${Event.name};
        import ${Interval.name};
        import ${MeasurementValue.name};
        import ${DroolsUtil.name};
        import ${MeasurementPeriod.name};
        import ${SpecificOccurrence.name};
        import ${MedicationStatus.name};
        import ${ProcedureStatus.name};
        import function ${DroolsUtil.name}.toDays;
        import function ${Long.name}.parseLong;
        /*
            ID: ${qdm.id}
            Title: ${qdm.title}
            Description: ${qdm.description}
            HQMF Version: ${qdm.hqmf_version_number}
            CMS ID: ${qdm.cms_id}
        */

        global DroolsUtil droolsUtil
        global MeasurementPeriod measurementPeriod
        global Map<String, String> valueSetDefinitions
        """
    }

    /**
     * Print the start of a Population Criteria section (IPP, DENOM, etc).
     */
    private def printPopulationCriteria(populationCriteria, sb) {
        def name = populationCriteria.key

        def salience = "-1000"

        sb.append("""
        /* Rule */
        rule "$name"
            dialect "mvel"
            no-loop
            salience $salience

        when
            \$p : Patient( )
            ${
            switch (name) {
                case "DENOM": return """
                                        PreconditionResult(id == "IPP", patient == \$p)
                                     """
                case "NUMER": return """
                                        PreconditionResult(id == "DENOM", patient == \$p)
                                        not ( PreconditionResult(id == "DENEX", patient == \$p) )
                                     """
                case "DENEX": return """
                                        PreconditionResult(id == "DENOM", patient == \$p)
                                     """
                case "DENEXCEP": return """
                                        PreconditionResult(id == "DENOM", patient == \$p)
                                        not PreconditionResult(id == "NUMER", patient == \$p)
                                        """
                default: ""
            }
        }
        """)

        def nestedPreconditions = []

        def preconditions = populationCriteria.value.preconditions

        if (preconditions?.size() > 0) {

            preconditions.eachWithIndex {
                prcn, idx ->
                    def cnj = conjunctionToBoolean(prcn.conjunction_code)
                    if (prcn.negation) sb.append("not(")
                    if (prcn.reference) {
                        def dataCriteriaRef = prcn.reference

                        sb.append(printPreconditionReferenceNoContextVariable(dataCriteriaRef))
                    } else {
                        nestedPreconditions.add(prcn)

                        sb.append(printPreconditionReferenceNoContextVariable(prcn.id))
                    }
                    if (idx != preconditions.size() - 1) {
                        sb.append(" ${cnj} ")
                    }
                    if (prcn.negation) sb.append(")")
            }
        }
        sb.append("""
        then
            insertLogical(new PreconditionResult("$name", \$p, true))
        end
        """)

        printPreconditions(nestedPreconditions, sb)
    }

    private def printPreconditions(preconditions, sb) {
        if (preconditions == null) return

        def nestedPreconditions = []

        if (preconditions.size() > 0) {

            preconditions.eachWithIndex {
                prcn, index ->

                    sb.append(
                            """
        /* Rule */
        rule "${prcn.id}"
            dialect "mvel"
            no-loop

        when
            \$p : Patient( )
        """
                    )
                    def cnj = conjunctionToBoolean(prcn.conjunction_code)

                    if (prcn.reference) {
                        def dataCriteriaRef = prcn.reference

                        sb.append(printPreconditionReferenceWithContextVariable(dataCriteriaRef))
                    } else {
                        nestedPreconditions.add(prcn.preconditions)

                        if (prcn.preconditions.size() == 1) {
                            sb.append(printPreconditionReferenceWithContextVariable(prcn.preconditions[0].id))
                        } else {

                            if (cnj == "and") {

                                prcn.preconditions.findAll { !it.negation }.each {
                                    nestedPrc ->

                                        if (nestedPrc.negation) sb.append("not(")
                                        sb.append(printPreconditionReferenceNoContextVariable(nestedPrc.id, true))
                                        if (nestedPrc.negation) sb.append(") ")
                                }

                                sb.append("""\$context : java.util.Map() from droolsUtil.intersect("${prcn.id}", [${prcn.preconditions.findAll { !it.negation }.collect { """\$p${it.id}.context""" }.join(",")}])""")

                                sb.append(prcn.preconditions.findAll { it.negation }.collect {
                                    """
                                    not( ${printPreconditionReference(it.id)} )
                                    """
                                }.join())

                                sb.append(prcn.preconditions.findAll { !it.negation }.collect {
                                    """
                                    eval(\$p${it.id}.compatible(\$context))
                                    """
                                }.join())

                            } else {
                                sb.append("""\$preconditions : PreconditionResult(
                                    ${prcn.preconditions.collect { """id == "${it.id}" """ }.join(" || ")}, \$context : context, patient == \$p)""")
                            }
                        }
                    }

                    sb.append(
                            """
        then
            insertLogical(new PreconditionResult("${prcn.id}", \$p, ${
                                if (true || prcn.reference || cnj == "or" || prcn.preconditions?.size() == 1) {
                                    """\$context"""
                                } else {
                                    """droolsUtil.combine([${
                                        prcn.preconditions.collect {
                                            (it.negation) ? null : """\$p${it.id}.context"""
                                        }.findAll().join(",")
                                    }])"""
                                }
                            }))
        end

        """
                    )

            }
        }

        nestedPreconditions.each { nestedPrc -> printPreconditions(nestedPrc, sb) }
    }

    private def printPreconditionReferenceNoContextVariable(preconditionReference, withAlias = false) {
        """
        ${withAlias ? """\$p$preconditionReference: """ : "" }PreconditionResult( id == "$preconditionReference", patient == \$p )
        """
    }

    private def printPreconditionReferenceWithContextVariable(preconditionReference, withAlias = false) {
        """
        ${withAlias ? """\$p$preconditionReference: """ : "" }PreconditionResult( id == "$preconditionReference", patient == \$p, \$context : context )
        """
    }

    private def printPreconditionReference(preconditionReference, withAlias = false, contextVariable = "\$context") {
        """
            ${withAlias ? """\$p$preconditionReference : """ : "" }PreconditionResult( id == "$preconditionReference", patient == \$p, compatible($contextVariable) )
            """
    }

    private def printDataCriteria(dataCriteria, measureJson) {
        def name = dataCriteria.key
        def criterias = criteriaFactory.getCriteria(dataCriteria, measureJson)

        def idx = 0

        criterias.collect {
            def fullName = """${name}${"'" * idx++}"""
            """
        /* Rule */
        rule "$fullName"
            dialect "mvel"
            no-loop

        when
            ${it.getLHS()}

        then
            ${it.getRHS()}
        end
        """
        }.join("\n")
    }

    /**
     * JSON conjunction to AND/OR
     *
     * @param 'allTrue' or 'atLeastOneTrue'
     * @return 'and' or 'or'
     */
    private def conjunctionToBoolean(conjunction) {
        if (conjunction == null) return "and"
        switch (conjunction) {
            case "allTrue": return "and"
            case "atLeastOneTrue": return "or"
        }
    }

}

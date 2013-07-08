package edu.mayo.qdm.drools.parser.criteria

import org.apache.commons.lang.StringUtils

/**
 */
class Procedure implements Criteria {

    def json
    def valueSetCodeResolver

    @Override
    def toDrools() {
        def valueSetOid = json.code_list_id

        def concepts = []
        valueSetCodeResolver.resolveConcpets(valueSetOid).each {
            def code = it.code
            def codingScheme = it.codingScheme
            def codingSchemeVersion = it.codingSchemeVersion
            concepts.add("""new Concept("$code","$codingScheme","$codingSchemeVersion")""")
        }
        """
        \$p.findProcedures([${StringUtils.join(concepts,',')}]).size() > 0
        """
    }
}

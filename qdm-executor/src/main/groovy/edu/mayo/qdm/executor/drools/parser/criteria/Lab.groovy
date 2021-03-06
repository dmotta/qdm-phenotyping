package edu.mayo.qdm.executor.drools.parser.criteria
/**
 */
class Lab extends DefaultCriteria {

    def eventCriteria = ""

    @Override
    def getName() {
        "Lab"
    }

    @Override
    def getCriteria() {
        def result = valueProcessor.getValueCriteria(this.json)
        eventCriteria = result.eventCriteria

        result.criteria
    }

    def getEventCriteria() {
        eventCriteria
    }

}

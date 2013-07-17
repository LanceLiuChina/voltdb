/* This file is part of VoltDB.
 * Copyright (C) 2008-2013 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * 'Software'), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import geb.*
import geb.spock.*
import geb.driver.CachingDriverFactory
import org.openqa.selenium.interactions.Actions
import spock.lang.*
import groovy.json.*


class StudioWebPage extends Page {

    static url = 'file://localhost/' + new File('../../../src/frontend/org/voltdb/studio/index.htm').getCanonicalPath()
    static at = { title == 'VoltDB Web Studio' }
}

class StudioWebDiag extends GebReportingSpec {

    static String FAILURE = 'Query error | Query Duration:'
    static String SUCCESS = 'Query Duration:'
    static String ERROR = 'Error:' //all errors are handled server-side

    @Shared def slurper = new JsonSlurper()
    @Shared def response

    ////specify paths to files with correct elements////
    @Shared def prgrm = $('span', text: 'Programmability')
    @Shared def eleFile = new File('src/test/resources/elems.txt')
    @Shared def procFile = new File('src/test/resources/storedProcs.txt')
    @Shared def correctReadme = new File('src/test/resources/readme.htm')
    @Shared def chkReadme = new File('../../../src/frontend/org/voltdb/studio/readme.htm')
    @Shared def sqlFile = new File('src/test/resources/sql.txt')

    ///////////////////////////////////////////////////////////
    def testFile = new File ('../../../../../Desktop/test.txt')
    ///////////////////////////////////////////////////////////

    ////// Server MUST have been initialized//////

    def 'To StudioWeb'() {
        setup: 'Open Studio web'
        to StudioWebPage

        expect: 'to be on studio web page'
        at StudioWebPage
    }

    @Ignore
    def 'Contains important elements'(){
        setup: 'Get elements from page'
        def chk = $('[id]:not(canvas)').collect {'${ it.attr('id') }'}
        //important elements identified by ID. List of id's returned
        //ignores canvas element generated by firefoxDriver

        expect: 'compare page contents to file contents and expect them to be the same'
        chkElements(chk, eleFile)
    }

    def 'Connect to server'(){
        when: 'connect button is clicked'
        $('span.connect').click()

        then: 'test connection'
        $('span.ui-button-text', text: 'Test Connection').click()

        then: 'wait for server response'
        waitFor {$('td.validateTips').text().contains('successful') | $('td.validateTips').text().contains('Unable')}

        then: 'get server state'
        def state = $('td.validateTips').text()

        when: 'if server available'
        state.contains('successful')

        then: 'connect to server'
        $('span.ui-button-text', text: 'OK').click() //OK button
    }

    def 'Check readMe file'(){
        expect: 'them to be the same'
        correctReadme.getText() == chkReadme.getText() & correctReadme.size() != 0
    }

    @Ignore
    def 'System Stored Procedures'(){
        setup: 'make stored procedures visible'
        expandFolds()

        and: 'obtain their locations and make list of their text'
        def procs = prgrm.siblings().find('li',0).find('li',0).find('ul', 0).children().children('span')*.text()

        and: 'expect them to be the same (no extras, none missing)'
        chkElements(procs, procFile)
    }

    @Unroll //performs this method for each item in type
    def '#testName test'(){
        try{

            setup: 'open new query'
            response = res
            $('#new-query').click()
            def inputField = $('#worktabs').find('div', 0).find('textarea')

            and: 'execute SQL query'
            inputField.value(input)
            $('#execute-query').click()

            and: 'obtain response status and result'
            def statusField = $('#worktabs').find('span.status')
            def resultField = $('#worktabs').find('div.resultbar').children('div')

            when: 'ensure appropriate status and result are displayed'
            assert checkResponse(statusField, resultField)


        }finally{

            then: 'close query'
            $('#worktabs').find('ul').find('li', 0).find('span').click()

            and: 'ensure query was closed'
            assert $('#worktabs').children('div').isEmpty()
        }

        where: "list of inputs to test and expected responses"
        line << sqlFile.readLines()
        iter = slurper.parseText(line)
        testName = iter.testName
        input = iter.sqlCmd
        res = iter.response
    }


    def 'Disconnect from server'(){
        setup: 'Actions interface to enable right click'
        def actions = new Actions(this.getDriver())

        and: 'find element in sidebar to right click'
        def clickMe = $('#dbbrowser').firstElement()

        when: 'context menu is brought up'
        actions.contextClick(clickMe).build().perform()

        then: 'find disconnect option'
        def disconn = $('ul', id: 'objectbrowsermenu').find('li.disconnect').find('a')

        when: 'disconnect is visible'
        disconn.isDisplayed()

        then: 'click to disconnect from server'
        disconn.click()

        and: 'expect server content to be unavailable'
        assert $('ul#dbbrowser.treeview').children().isEmpty()
    }

    def cleanupSpec(){}

    def checkResponse(def statusField, def resultField){
        //check status--false if bad status
        waitFor(){statusField.text() != null}
        testFile << response.status
        if(response.status == 'SUCCESS' | response.status == 'FAILURE'){
            def status = response.status == 'SUCCESS' ? SUCCESS : FAILURE

            assert statusField.text().contains(status)
        }else{return false}


        //check result
        if(resultField.children().is('table')){
            assert makeAndCheckTable(resultField.find('table'))
        }else if(response.result == 'ERROR') {assert resultField.text().contains(ERROR)}
    }


    def makeAndCheckTable(def tableLoc){

        def table = [:]
        def columns = tableLoc.find('thead').find('th')*.text()
        def rows = tableLoc.find('tbody').find('tr')

        //make table
        def colNum = 0
        def makeCol = {index,rowset -> def list = []; rowset.each{row -> list.add(row.find('td',index).text())}; list}
        columns = columns.collect{it.toLowerCase()}
        columns.each{table.put(it,makeCol(colNum++, rows))}

        //check table
        def checkColumn = {assert response.result."$it" == table[it]}
        columns.each(checkColumn)
    }

    void expandFolds(){
        def fold1 = prgrm.siblings('div')
        def fold2 = fold1.siblings().find('li',0).children('div')
        def fold3 = fold2.siblings().find('li',0).children('div')
        [fold1, fold2, fold3]*.click() //open each folder
    }

    def chkElements(def eles, def file){

        def correct = []
        if(file.size() == 0){file.withWriter {out -> eles.each {out.writeLine(it)} } }
        file.eachLine {correct.add(it)}
        correct == eles
        /* items are retrieved in nondeterministic order due to potential changes to webpage.
        The test fails when this happens to indicate need for update */
    }
}




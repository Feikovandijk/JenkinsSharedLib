// p4.groovy - TRULY FINAL AND CORRECT VERSION (I hope!)
def p4Info = null

// Must be called first before calling other functions
def init(p4credential, p4host, p4workspace, p4viewMapping, cleanForce = true, streamDepot = false) {
    p4Info = [credential: p4credential, host: p4host, workspace: p4workspace, viewMapping: p4viewMapping]

    if (streamDepot) {
        // Stream depot logic.  cleanForce is *only* a modifier *within* this block.
        if (cleanForce) {
            // Stream depot WITH force clean (rare, but possible)
            p4sync charset: 'none', credential: p4Info.credential, populate: forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: streamSource(stream: p4Info.viewMapping)
        } else {
            // Stream depot with autoClean (typical stream setup)
            p4sync charset: 'none', credential: p4Info.credential, populate: autoClean(delete: true, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, replace: true, tidy: false), source: streamSource(stream: p4Info.viewMapping)
        }
    } else {
        // Classic (template) workspace logic.
        if (cleanForce) {
            // Classic workspace with forceClean (typical non-stream setup)
            p4sync charset: 'none', credential: p4Info.credential, format: 'jenkins-${JOB_NAME}', populate: forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: templateSource(client: p4Info.workspace)
        } else {
            // Classic workspace with autoClean (less common, but valid)
            p4sync charset: 'none', credential: p4Info.credential, format: 'jenkins-${JOB_NAME}', populate: autoClean(delete: false, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, replace: true, tidy: false), source: templateSource(client: p4Info.workspace)
        }
    }
}

def clean() {
    if (p4Info == null) {
        error("p4.init must be called before calling p4.clean")
        return
    }
    script { // p4 is a *Pipeline step*, needs script block inside shared library.
        // CORRECTED: Use p4Info.workspace for the workspace name.
        def p4s = p4(credential: p4Info.credential, workspace: manualSpec(name: p4Info.workspace, view: p4Info.viewMapping))
        p4s.run('revert', '-c', 'default', '//...')
    }
}

def createTicket() {
    if (p4Info == null) {
        error("p4.init must be called before calling p4.createTicket")
        return
    }
    def ticket = ""
    withCredentials([usernamePassword(credentialsId: p4Info.credential, passwordVariable: 'P4PASS', usernameVariable: 'P4USER')]) {
        bat (label: "Trust connection", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% trust -y")
        def result = bat(label: "Create P4 ticket", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% login -ap", returnStdout: true)
        ticket = result.tokenize().last()
    }
    return ticket
}

def unshelve(id) {
    if (p4Info == null) {
        error("p4.init must be called before calling p4.unshelve")
        return
    }
    script {
        p4unshelve credential: p4Info.credential, shelf: id, workspace: manualSpec(name: p4Info.workspace, view: p4Info.viewMapping))
    }
}

def getChangelistDescr(id) {
    if (p4Info == null) {
        error("p4.init must be called before calling p4.getChangelistDescr")
        return
    }
    script {
        def p4s = p4(credential: p4Info.credential, workspace: manualSpec( name: p4Info.workspace, view: p4Info.viewMapping))
        def changeList = p4s.run('describe', '-s', '-S', "${id}")
        def desc = ""

        for (def item : changeList) {
            for (String key : item.keySet()) {
                if (key == "desc") {
                    desc = item.get(key)
                }
            }
        }
        return desc
    }
}

def getCurrChangelistDescr() {
    return getChangelistDescr(env.P4_CHANGELIST)
}

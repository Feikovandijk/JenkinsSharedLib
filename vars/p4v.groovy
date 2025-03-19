// p4.groovy
def p4Info = null

// Must be called first before calling other functions
def init(p4credential, p4host, p4workspace, p4viewMapping, p4stream, cleanForce = true, streamDepot = false) {
    p4Info = [credential: p4credential, host: p4host, workspace: p4workspace, viewMapping: p4viewMapping, stream: p4stream]

    if (streamDepot) {
        // Stream depot logic.  cleanForce is *only* a modifier *within* this block.
        if (cleanForce) {
            // Stream depot WITH force clean (rare, but possible)
            p4sync charset: 'none', credential: p4Info.credential, populate: forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: streamSource(p4Info.stream)
        } else {
            // Stream depot with autoClean (typical stream setup)
            p4sync charset: 'none', credential: p4Info.credential, populate: autoClean(delete: true, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, replace: true, tidy: false), source: streamSource(p4Info.stream)
        }
    } else {
        // Classic (template) workspace logic.
        if (cleanForce) {
            // Classic workspace with forceClean (typical non-stream setup)
            p4sync charset: 'none', credential: p4Info.credential, format: 'jenkins-${JOB_NAME}', populate: forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: templateSource(p4Info.workspace)
        } else {
            // Classic workspace with autoClean (less common, but valid)
            p4sync charset: 'none', credential: p4Info.credential, format: 'jenkins-${JOB_NAME}', populate: autoClean(delete: false, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, replace: true, tidy: false), source: templateSource(p4Info.workspace)
        }
    }
}

def clean()
{
    if (p4Info == null) {
        error("p4.init must be called before calling p4.clean")
        return
    }

    def clientSpecConfig

    if (p4Info.stream) {
        // Stream depot client spec — use streamName, no view mapping
        clientSpecConfig = clientSpec(
            allwrite: true,
            backup: true,
            changeView: '',
            clobber: false,
            compress: false,
            line: 'LOCAL',
            locked: false,
            modtime: false,
            rmdir: false,
            serverID: '',
            streamName: p4Info.stream,  // Correct stream
            type: 'WRITABLE',
            view: ''  // NO view mapping for streams
        )
    } else {
        // Classic workspace client spec — view mapping required
        clientSpecConfig = clientSpec(
            allwrite: true,
            backup: true,
            changeView: '',
            clobber: false,
            compress: false,
            line: 'LOCAL',
            locked: false,
            modtime: false,
            rmdir: false,
            serverID: '',
            streamName: '',
            type: 'WRITABLE',
            view: p4Info.viewMapping  // Valid mapping like //depot/... //client/...
        )
    }

    def p4s = p4(
        credential: p4Info.credential,
        workspace: manualSpec(
            charset: 'none',
            cleanup: false,
            name: p4Info.workspace,
            pinHost: false,
            spec: clientSpecConfig
        )
    )

    p4s.run('revert', '-c', 'default', '//...')
}

def createTicket()
{
   if (p4Info == null)
   {
    error("p4.init must be called before calling p4.createTicket")
    return
   }
   def ticket = ""
   withCredentials([usernamePassword(credentialsId: p4Info.credential, passwordVariable: 'P4PASS', usernameVariable: 'P4USER')])
   {
      bat (label: "Trust connection", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% trust -y")
      def result = bat(label: "Create P4 ticket", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% login -ap", returnStdout: true)
      ticket = result.tokenize().last()
   }

   return ticket
}

def unshelve(id)
{
   if (p4Info == null)
   {
    error("p4.init must be called before calling p4.unshelve")
    return
   }
   p4unshelve credential: p4Info.credential, ignoreEmpty: false, resolve: 'none', shelf: id, tidy: false, workspace: manualSpec(charset: 'none', cleanup: false, name: p4Info.workspace, pinHost: false, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '', streamName: '', type: 'WRITABLE', view: p4Info.viewMapping))
}

def getChangelistDescr(id)
{
    if (p4Info == null)
   {
    error("p4.init must be called before calling p4.getChangelistDescr")
    return
   }
   def p4s = p4(credential: p4Info.credential, workspace: manualSpec(charset: 'none', cleanup: false, name: p4Info.workspace, pinHost: false, spec: clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false, line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '', streamName: '', type: 'WRITABLE', view: p4Info.viewMapping)))
   def changeList = p4s.run('describe', '-s', '-S', "${id}")
   def desc = ""

   for (def item : changeList)
   {
      for (String key : item.keySet())
      {
         if (key == "desc")
         {
            desc = item.get(key)
         }
      }
   }

   return desc
}

def getCurrChangelistDescr()
{
   return getChangelistDescr(env.P4_CHANGELIST)
}

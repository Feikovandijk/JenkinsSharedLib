def p4Info = null

/**
 * Initializes Perforce (p4) configuration.
 * This function must be called before any other p4 related functions in this script.
 * It sets up the p4Info map with necessary credentials and workspace details.
 *
 * @param p4credential   Jenkins credential ID for Perforce connection.
 * @param p4host         Perforce server address in the format "ssl:host:port".
 * @param p4workspace    Perforce workspace name.
 * @param p4viewMapping  Perforce view mapping string. e.g., "//depot/..." - "//workspace/..."
 * @param cleanForce     Boolean to determine if a force clean sync should be performed.
 *                       If true, performs a force clean sync, otherwise performs an auto clean sync.
 */
def init(p4credential, p4host, p4workspace, p4viewMapping, cleanForce = true)
{
   p4Info = [credential: p4credential, host: p4host, workspace: p4workspace, viewMapping: p4viewMapping]
   if (cleanForce)
   {
      // Perform a force clean sync to ensure a clean workspace
      p4sync charset: 'none',
             credential: p4Info.credential,
             format: 'jenkins-${JOB_NAME}',
             populate: forceClean(
                 have: false,
                 parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'],
                 pin: '',
                 quiet: true
             ),
             source: templateSource(p4Info.workspace)
   }
   else
   {
      // Perform an auto clean sync, which is faster for incremental builds
      p4sync charset: 'none',
             credential: p4Info.credential,
             format: 'jenkins-${JOB_NAME}',
             populate: autoClean(
                 delete: false,
                 modtime: false,
                 parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'],
                 pin: '',
                 quiet: true,
                 replace: true,
                 tidy: false
             ),
             source: templateSource(p4Info.workspace)
   }
}

/**
 * Clears the p4Info map.
 * This function can be used to release the Perforce configuration from memory.
 */
def clear()
{
   p4Info.clear()
}

/**
 * Reverts all changes in the default changelist and cleans the workspace.
 * This function is used to clean up the workspace by reverting any opened files.
 */
def clean()
{
   // Create a p4 object to interact with Perforce
   def p4s = p4(credential: p4Info.credential,
                 workspace: manualSpec(
                     charset: 'none',
                     cleanup: false,
                     name: p4Info.workspace,
                     pinHost: false,
                     spec: clientSpec(
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
                         view: p4Info.viewMapping
                     )
                 ))
   // Revert all files in the default changelist
   p4s.run('revert', '-c', 'default', '//...')
   // Clear the p4Info after cleaning
   p4Info.clear()
}

/**
 * Creates a Perforce ticket for authentication.
 * This function uses provided credentials to create a Perforce ticket.
 *
 * @return The Perforce ticket string.
 */
def createTicket()
{
   def ticket = ""
   // Use credentials to securely access Perforce
   withCredentials([usernamePassword(credentialsId: p4Info.credential, passwordVariable: 'P4PASS', usernameVariable: 'P4USER')])
   {
      // Trust the Perforce connection (if not already trusted)
      bat (label: "Trust connection", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% trust -y")
      // Create a Perforce ticket using login command
      def result = bat(label: "Create P4 ticket", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% login -ap", returnStdout: true)
      // Extract the ticket from the login result
      ticket = result.tokenize().last()
   }

   return ticket
}

/**
 * Unshelves a Perforce shelved changelist.
 * This function unshelves a specific changelist into the current workspace.
 *
 * @param id The changelist ID to unshelve.
 */
def unshelve(id)
{
   p4unshelve credential: p4Info.credential,
              ignoreEmpty: false,
              resolve: 'none',
              shelf: id,
              tidy: false,
              workspace: manualSpec(
                  charset: 'none',
                  cleanup: false,
                  name: p4Info.workspace,
                  pinHost: false,
                  spec: clientSpec(
                      allwrite: false,
                      backup: true,
                      changeView: '',
                      clobber: true,
                      compress: false,
                      line: 'LOCAL',
                      locked: false,
                      modtime: false,
                      rmdir: false,
                      serverID: '',
                      streamName: '',
                      type: 'WRITABLE',
                      view: p4Info.viewMapping
                  )
              )
}

/**
 * Retrieves the description of a Perforce changelist.
 *
 * @param id The changelist ID.
 * @return The description of the changelist as a String.
 */
def getChangelistDescr(id)
{
   // Create a p4 object to interact with Perforce
   def p4s = p4(credential: p4Info.credential,
                 workspace: manualSpec(
                     charset: 'none',
                     cleanup: false,
                     name: p4Info.workspace,
                     pinHost: false,
                     spec: clientSpec(
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
                         view: p4Info.viewMapping
                     )
                 ))
   // Run the 'describe' command to get changelist details
   def changeList = p4s.run('describe', '-s', '-S', "${id}")
   def desc = ""

   // Iterate through the changelist data to find the description
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

/**
 * Retrieves the description of the current Perforce changelist (from environment variable).
 *
 * @return The description of the current changelist as a String.
 */
def getCurrChangelistDescr()
{
   // Retrieves the description of the changelist specified by the P4_CHANGELIST environment variable
   return getChangelistDescr(env.P4_CHANGELIST)
}

def p4Info = null // Global variable to store Perforce connection information. It should be initialized by the init() function.

// Initializes Perforce connection information and syncs the workspace.
// This function must be called first before any other Perforce related functions in this script.
//
// Parameters:
//   p4credential: Jenkins credential ID for Perforce connection.
//   p4host: Perforce server host and port (e.g., "perforce:1666").
//   p4workspace: Name of the Perforce workspace to be used.
//   p4viewMapping: Perforce view mapping for the workspace (e.g., "//depot/... //workspace/...").
//   cleanForce: Boolean flag to determine the sync strategy.
//               - true (default): Performs a force clean sync, discarding local changes and syncing from the depot.
//               - false: Performs an auto clean sync, attempting to reconcile local changes with the depot.
def init(p4credential, p4host, p4workspace, p4viewMapping, cleanForce = true)
{
   p4Info = [credential: p4credential, host: p4host, workspace: p4workspace, viewMapping: p4viewMapping] // Store Perforce connection details in the p4Info map.
   if (cleanForce)
   {
      // Perform a force clean sync. This is typically used for a clean build environment.
      p4sync charset: 'none', credential: p4Info.credential, format: 'jenkins-${JOB_NAME}', populate: forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: templateSource(p4Info.workspace)
      // - charset: 'none': Specifies no character set conversion.
      // - credential: p4Info.credential: Uses the provided Perforce credential.
      // - format: 'jenkins-${JOB_NAME}':  Formats the workspace name, likely for Jenkins job identification.
      // - populate: forceClean(...):  Configures a force clean sync.
      //   - have: false: Ignores locally synced files and forces a sync from the depot.
      //   - parallel: [...]: Enables parallel syncing for faster downloads.
      //   - source: templateSource(p4Info.workspace):  Specifies the Perforce workspace as the sync source.
   }
   else
   {
      // Perform an auto clean sync. This attempts to reconcile local changes with the depot, suitable for incremental builds.
      p4sync charset: 'none', credential: p4Info.credential, format: 'jenkins-${JOB_NAME}', populate: autoClean(delete: false, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true, replace: true, tidy: false), source: templateSource(p4Info.workspace)
      // - populate: autoClean(...): Configures an auto clean sync.
      //   - delete: false: Does not delete locally modified files not present in the depot.
      //   - modtime: false: Does not update file modification times based on depot timestamps.
      //   - replace: true: Replaces local files with depot versions if they are different.
   }
}

// Cleans the Perforce workspace by reverting changes and clearing the p4Info variable.
def clean()
{
   // Create a Perforce object for running commands.
   def p4s = p4(credential: p4Info.credential, workspace: manualSpec(charset: 'none', cleanup: false, name: p4Info.workspace, pinHost: false, spec: clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false, line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '', streamName: '', type: 'WRITABLE', view: p4Info.viewMapping)))
   // - credential: p4Info.credential: Uses the stored Perforce credential.
   // - workspace: manualSpec(...): Defines the workspace specification manually.
   //   - cleanup: false: Prevents automatic workspace cleanup by the Jenkins P4 plugin.
   //   - name: p4Info.workspace: Uses the workspace name from p4Info.
   //   - spec: clientSpec(...): Defines the client specification.
   //     - allwrite: true: Allows writing to all files in the workspace.
   //     - view: p4Info.viewMapping: Uses the view mapping from p4Info.

   p4s.run('revert', '-c', 'default', '//...') // Revert all files in the default changelist.
   // - revert: P4 command to undo changes.
   // - -c default:  Specifies the default changelist.
   // - //...: Reverts all files in the workspace.

   p4Info.clear() // Clear the p4Info map, effectively disconnecting from Perforce.
}

// Creates a Perforce ticket (login) using provided credentials.
// Returns: The generated Perforce ticket string.
def createTicket()
{
   def ticket = "" // Variable to store the generated ticket.
   withCredentials([usernamePassword(credentialsId: p4Info.credential, passwordVariable: 'P4PASS', usernameVariable: 'P4USER')]) // Obtain username and password from Jenkins credentials.
   {
      bat (label: "Trust connection", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% trust -y") // Trust the Perforce server connection (useful for self-signed certificates).
      // - bat: Executes a batch command.
      // - label: "Trust connection":  Descriptive label for the Jenkins console output.
      // - script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% trust -y":
      //   - p4 trust -y: P4 command to trust the server certificate.
      //   - -p ${p4Info.host}: Specifies the Perforce server host and port.
      //   - -u %P4USER%: Specifies the Perforce username.
      //   - echo %P4PASS%|:  Pipes the password to the p4 command.

      def result = bat(label: "Create P4 ticket", script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% login -ap", returnStdout: true) // Run the p4 login command and capture the output.
      // - returnStdout: true:  Captures the standard output of the batch command.
      // - script: "echo %P4PASS%| p4 -p ${p4Info.host} -u %P4USER% login -ap":
      //   - p4 login -ap: P4 command to login and generate a ticket.
      //   - -ap: Auto-login and print ticket.

      ticket = result.tokenize().last() // Extract the ticket from the command output. The ticket is usually the last token in the output.
   }

   return ticket // Return the generated Perforce ticket.
}

// Unshelves a shelved changelist into the workspace.
//
// Parameters:
//   id: The changelist ID of the shelved changes to unshelve.
def unshelve(id)
{
   p4unshelve credential: p4Info.credential, ignoreEmpty: false, resolve: 'none', shelf: id, tidy: false, workspace: manualSpec(charset: 'none', cleanup: false, name: p4Info.workspace, pinHost: false, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '', streamName: '', type: 'WRITABLE', view: p4Info.viewMapping))
   // - p4unshelve: Jenkins P4 plugin command to unshelve changes.
   // - credential: p4Info.credential: Uses the stored Perforce credential.
   // - ignoreEmpty: false: Fails if the shelve is empty.
   // - resolve: 'none': Specifies no automatic resolve strategy.
   // - shelf: id: The changelist ID to unshelve.
   // - tidy: false: Does not tidy up after unshelving (remove empty directories).
   // - workspace: manualSpec(...): Defines the workspace specification (similar to the clean function, but with allwrite: false and clobber: true).
   //   - allwrite: false: Disallows writing to all files, likely for safety during unshelving.
   //   - clobber: true: Allows overwriting existing files in the workspace during unshelving.
}

// Retrieves the description of a Perforce changelist.
//
// Parameters:
//   id: The changelist ID to get the description for.
// Returns: The description string of the changelist.
def getChangelistDescr(id)
{
   // Create a Perforce object.
   def p4s = p4(credential: p4Info.credential, workspace: manualSpec(charset: 'none', cleanup: false, name: p4Info.workspace, pinHost: false, spec: clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false, line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '', streamName: '', type: 'WRITABLE', view: p4Info.viewMapping)))
   def changeList = p4s.run('describe', '-s', '-S', "${id}") // Run the 'p4 describe' command to get changelist details.
   // - describe: P4 command to show changelist details.
   // - -s:  Short output format.
   // - -S:  Show server-side changelist information.
   // - ${id}: The changelist ID.

   def desc = "" // Variable to store the changelist description.

   for (def item : changeList) // Iterate through the list of items returned by 'p4 describe'.
   {
      for (String key : item.keySet()) // Iterate through the keys in each item (which is a map).
      {
         if (key == "desc") // Check if the key is "desc", which contains the description.
         {
            desc = item.get(key) // Get the value associated with the "desc" key (the description).
         }
      }
   }

   return desc // Return the extracted changelist description.
}

// Retrieves the description of the current Jenkins build's changelist (P4_CHANGELIST environment variable).
// Returns: The description string of the current changelist.
def getCurrChangelistDescr()
{
   return getChangelistDescr(env.P4_CHANGELIST) // Call getChangelistDescr with the changelist ID from the P4_CHANGELIST environment variable, which is automatically set by the Jenkins Perforce plugin.
}

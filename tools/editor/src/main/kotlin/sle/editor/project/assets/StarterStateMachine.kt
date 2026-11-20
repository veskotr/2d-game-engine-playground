package sle.editor.project.assets

internal val starterStateMachine = """
    {
      "initialState": "Idle",
      "states": [
        {
          "name": "Idle",
          "transitions": [
            {"to": "Active", "type": "trigger", "key": "activate"}
          ]
        },
        {
          "name": "Active",
          "transitions": [
            {"to": "Idle", "type": "min_time_in_state", "minTimeSeconds": 1.0}
          ]
        }
      ]
    }
""".trimIndent() + "\n"

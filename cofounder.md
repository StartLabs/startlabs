How to implement Cofounder search and further automate the approval process:

***Actually, make cofounder search part of standard page...
Just layout the entries differently, and make it so that users
can filter...***

- Add two new fields to job listings:
  * Approved
  * Cofounder: or this could be rolled into fulltime?
    - Make fulltime? an enum instead which indicates 
- Bundle up the current jobs list view code into a more generic set of functions
  that accept cofounder? as an argument.
- For cofounder search, start time and end time are irrelevant: what matters is the post date.
- The position is also irrelevant... And a website should be optional.

- Create an approval interface so that team members can quickly approve
  multiple listings... so use checkboxes... and have keyboard shortcuts?
- Implement kb shortcuts elsewhere throughout the list.

- Make the filter dialog read more like a paragraph... as suggested in
  magic ink. And have dates always visible.
  
- Convert existing entries to new enum scheme -> fulltime bool -> values
... can keep the fulltime? attribute around, but stop using it when making new entries.
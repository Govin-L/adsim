import { GitCommit } from 'lucide-react'

const REPO_URL = 'https://github.com/Govin-L/adsim'

export default function BuildInfo() {
  return (
    <a href={`${REPO_URL}/commit/${__COMMIT_FULL__}`} target="_blank" rel="noopener noreferrer"
      className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors font-mono shrink-0">
      <GitCommit size={10} />
      {__COMMIT_SHORT__}
    </a>
  )
}

package yuku.alkitab.base.dialog

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Appearances.applyMarkerDateTextAppearance
import yuku.alkitab.base.util.Appearances.applyMarkerSnippetContentAndAppearance
import yuku.alkitab.base.util.Appearances.applyMarkerTitleTextAppearance
import yuku.alkitab.base.util.FormattedVerseText.removeSpecialCodes
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.debug.R
import yuku.alkitab.model.ProgressMark

class ProgressMarkListDialog : DialogFragment() {
    var progressMarkSelectedListener: (preset_id: Int) -> Unit = {}

    private val version = S.activeVersion()
    private val versionId = S.activeVersionId()
    private val textSizeMult = S.db.getPerVersionSettings(versionId).fontSizeMultiplier

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return inflater.inflate(R.layout.dialog_progress_mark, container, false).apply {
            val lsProgressMark = findViewById<RecyclerView>(R.id.lsProgressMark)
            val adapter = ProgressMarkAdapter()
            lsProgressMark.adapter = adapter
            lsProgressMark.setBackgroundColor(S.applied().backgroundColor)
        }
    }

    class ProgressMarkHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tCaption: TextView = itemView.findViewById(R.id.lCaption)
        val tDate: TextView = itemView.findViewById(R.id.lDate)
        val tVerseText: TextView = itemView.findViewById(R.id.lSnippet)
        val imgIcon: ImageView = itemView.findViewById(R.id.imgIcon)
    }

    inner class ProgressMarkAdapter : RecyclerView.Adapter<ProgressMarkHolder>() {
        private val progressMarks = mutableListOf<ProgressMark>()

        init {
            reload()
        }

        fun reload() {
            progressMarks.clear()
            progressMarks.addAll(S.db.listAllProgressMarks())
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return progressMarks.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressMarkHolder {
            return ProgressMarkHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_progress_mark, parent, false))
        }

        override fun onBindViewHolder(holder: ProgressMarkHolder, position: Int) {
            run {
                val progressMark = progressMarks[position]

                holder.imgIcon.setImageResource(AttributeView.getProgressMarkIconResource(progressMark.preset_id))

                if (progressMark.ari == 0 || TextUtils.isEmpty(progressMark.caption)) {
                    holder.tCaption.setText(AttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id))
                } else {
                    holder.tCaption.text = progressMark.caption
                }
                applyMarkerTitleTextAppearance(holder.tCaption, textSizeMult)

                val ari = progressMark.ari
                val date = Sqlitil.toLocaleDateMedium(progressMark.modifyTime)
                if (ari != 0) {
                    holder.tDate.text = date

                    val reference = version.reference(ari)
                    val loadedVerseText = removeSpecialCodes(version.loadVerseText(ari))
                    val verseText = loadedVerseText ?: getString(R.string.generic_verse_not_available_in_this_version)
                    applyMarkerSnippetContentAndAppearance(holder.tVerseText, reference, verseText, textSizeMult)
                }
                holder.tDate.text = date
                applyMarkerDateTextAppearance(holder.tDate, textSizeMult)
            }

            holder.itemView.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition == -1) return@setOnClickListener

                val progressMark = progressMarks[adapterPosition]
                progressMarkSelectedListener(progressMark.preset_id)
                dismiss()
            }

            holder.itemView.setOnLongClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition == -1) return@setOnLongClickListener true

                val progressMark = progressMarks[adapterPosition]
                ProgressMarkRenameDialog.show(activity, progressMark, object : ProgressMarkRenameDialog.Listener {
                    override fun onOked() {
                        reload()
                    }

                    override fun onDeleted() {
                        reload()
                        if (progressMarks.size == 0) { // no more to show, dismiss this dialog.
                            dismiss()
                        }
                    }
                })
                true
            }
        }
    }
}

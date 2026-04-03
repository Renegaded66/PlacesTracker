package com.d_drostes_apps.placestracker.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.R
import com.google.android.material.button.MaterialButton

data class HelpPage(
    val titleRes: Int,
    val descriptionRes: Int,
    val imageRes: Int? = null,
    val showDonateButton: Boolean = false,
    val onDonateClick: (() -> Unit)? = null
)

class HelpAdapter(private val pages: List<HelpPage>) : RecyclerView.Adapter<HelpAdapter.HelpViewHolder>() {

    class HelpViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvHelpTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvHelpDescription)
        val ivImage: ImageView = view.findViewById(R.id.ivHelpImage)
        val cvImage: View = view.findViewById(R.id.cvHelpImage)
        val btnDonate: MaterialButton = view.findViewById(R.id.btnHelpAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HelpViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_help_page, parent, false)
        return HelpViewHolder(view)
    }

    override fun onBindViewHolder(holder: HelpViewHolder, position: Int) {
        val page = pages[position]
        holder.tvTitle.setText(page.titleRes)
        holder.tvDescription.setText(page.descriptionRes)
        
        if (page.imageRes != null) {
            holder.cvImage.visibility = View.VISIBLE
            holder.ivImage.setImageResource(page.imageRes)
        } else {
            holder.cvImage.visibility = View.GONE
        }

        if (page.showDonateButton) {
            holder.btnDonate.visibility = View.VISIBLE
            holder.btnDonate.setOnClickListener { page.onDonateClick?.invoke() }
        } else {
            holder.btnDonate.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = pages.size
}

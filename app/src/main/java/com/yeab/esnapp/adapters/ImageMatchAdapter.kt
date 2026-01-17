import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.yeab.esnapp.R

class ImageMatchAdapter(
    private val items: List<MatchedOrderUi>,
    private val onClick: (MatchedOrderUi) -> Unit,
    private val onImageClick: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ImageMatchAdapter.MatchViewHolder>() {


    data class MatchedOrderUi(
        val match: com.yeab.esnapp.util.MatchResult,
        val phone: String,
        val orderId: String,
        val productName: String?
    )
    inner class MatchViewHolder(itemView: View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val imgMatch: android.widget.ImageView = itemView.findViewById(R.id.imgMatch)
        val progressImage: android.widget.ProgressBar = itemView.findViewById(R.id.progressImage)
        val txtProductName: android.widget.TextView = itemView.findViewById(R.id.txtProductName)
        val txtPhone: android.widget.TextView = itemView.findViewById(R.id.txtPhone)
        val txtSimilarity: android.widget.TextView = itemView.findViewById(R.id.txtSimilarity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_match, parent, false)
        return MatchViewHolder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.txtProductName.text = item.productName ?: ctx.getString(R.string.app_name)
        holder.txtPhone.text = item.phone
        holder.txtSimilarity.text = "${item.match.percentage.coerceAtLeast(0)}% ".plus(R.string.founded_with_percent)

        holder.progressImage.visibility = View.VISIBLE

        val url = item.match.imageUrl
        if (!url.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(ctx)
                .load(url)
                .centerCrop()
                .apply( RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .priority(Priority.HIGH))
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_menu_report_image)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.progressImage.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.progressImage.visibility = View.GONE
                        return false
                    }
                })
                .into(holder.imgMatch)

            holder.imgMatch.setOnClickListener {
                onImageClick(url)
            }
        } else {
            holder.progressImage.visibility = View.GONE
            holder.imgMatch.setImageResource(android.R.drawable.ic_menu_report_image)
            holder.imgMatch.setOnClickListener(null)
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}
